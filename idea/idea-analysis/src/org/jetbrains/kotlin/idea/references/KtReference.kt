/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.firSymbolProvider
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.fir.firResolveState
import org.jetbrains.kotlin.idea.fir.getOrBuildFir
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import java.util.*

interface KtReference : PsiPolyVariantReference {
    fun resolveToDescriptors(bindingContext: BindingContext): Collection<DeclarationDescriptor>

    override fun getElement(): KtElement

    val resolvesByNames: Collection<Name>
}

abstract class AbstractKtReference<T : KtElement>(element: T) : PsiPolyVariantReferenceBase<T>(element), KtReference {
    val expression: T
        get() = element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        @Suppress("UNCHECKED_CAST")
        val kotlinResolver = KOTLIN_RESOLVER as ResolveCache.PolyVariantResolver<AbstractKtReference<T>>
        return ResolveCache.getInstance(expression.project).resolveWithCaching(this, kotlinResolver, false, incompleteCode)
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return matchesTarget(element)
    }

    override fun getCanonicalText(): String = "<TBD>"

    open fun canRename(): Boolean = false
    override fun handleElementRename(newElementName: String): PsiElement? = throw IncorrectOperationException()

    override fun bindToElement(element: PsiElement): PsiElement = throw IncorrectOperationException()

    @Suppress("UNCHECKED_CAST")
    override fun getVariants(): Array<Any> = PsiReference.EMPTY_ARRAY as Array<Any>

    override fun isSoft(): Boolean = false

    override fun resolveToDescriptors(bindingContext: BindingContext): Collection<DeclarationDescriptor> {
        return getTargetDescriptors(bindingContext)
    }

    protected abstract fun getTargetDescriptors(context: BindingContext): Collection<DeclarationDescriptor>

    override fun toString() = this::class.java.simpleName + ": " + expression.text

    companion object {
        class KotlinReferenceResolver : ResolveCache.PolyVariantResolver<AbstractKtReference<KtElement>> {
            class KotlinResolveResult(element: PsiElement) : PsiElementResolveResult(element)

            private fun FirResolvedTypeRef.toTargetPsi(session: FirSession): PsiElement? {
                val type = type as? ConeLookupTagBasedType ?: return null
                return (type.lookupTag.toSymbol(session) as? AbstractFirBasedSymbol<*>)?.fir?.psi
            }

            private fun ClassId.toTargetPsi(session: FirSession): PsiElement? {
                return (ConeClassLikeLookupTagImpl(this).toSymbol(session) as? AbstractFirBasedSymbol<*>)?.fir?.psi
            }

            private fun resolveToPsiElements(ref: AbstractKtReference<KtElement>): Collection<PsiElement> {
                val expression = ref.expression
                val state = expression.firResolveState()
                val session = state.getSession(expression)
                when (val fir = expression.getOrBuildFir(state)) {
                    is FirQualifiedAccess -> {
                        val calleeReference = fir.calleeReference as? FirResolvedCallableReference ?: return emptyList()
                        val target = (calleeReference.resolvedSymbol as? AbstractFirBasedSymbol<*>)?.fir
                        return listOfNotNull(target?.psi)
                    }
                    is FirResolvedTypeRef -> {
                        return listOfNotNull(fir.toTargetPsi(session))
                    }
                    is FirResolvedQualifier -> {
                        val classId = fir.classId ?: return emptyList()
                        return listOfNotNull(classId.toTargetPsi(session))
                    }
                    is FirAnnotationCall -> {
                        val type = fir.typeRef as? FirResolvedTypeRef ?: return emptyList()
                        return listOfNotNull(type.toTargetPsi(session))
                    }
                    is FirResolvedImport -> {
                        val classId = fir.resolvedClassId
                        if (classId != null) {
                            return listOfNotNull(classId.toTargetPsi(session))
                        }
                        val name = fir.importedName ?: return emptyList()
                        val symbolProvider = session.firSymbolProvider
                        return symbolProvider.getTopLevelCallableSymbols(fir.packageFqName, name).mapNotNull { it.fir.psi } +
                                listOfNotNull(symbolProvider.getClassLikeSymbolByFqName(ClassId(fir.packageFqName, name))?.fir?.psi)
                    }
                    else -> error("Unsupported KtReference target FIR: ${fir.render()} of ${fir.javaClass}")
                }
            }

            private fun resolveToPsiElements(ref: AbstractKtReference<KtElement>, context: BindingContext, targetDescriptors: Collection<DeclarationDescriptor>): Collection<PsiElement> {
                if (targetDescriptors.isNotEmpty()) {
                    return targetDescriptors.flatMap { target -> resolveToPsiElements(ref, target) }.toSet()
                }

                val labelTargets = getLabelTargets(ref, context)
                if (labelTargets != null) {
                    return labelTargets
                }

                return Collections.emptySet()
            }

            private fun resolveToPsiElements(
                ref: AbstractKtReference<KtElement>,
                targetDescriptor: DeclarationDescriptor
            ): Collection<PsiElement> {
                return if (targetDescriptor is PackageViewDescriptor) {
                    val psiFacade = JavaPsiFacade.getInstance(ref.expression.project)
                    val fqName = targetDescriptor.fqName.asString()
                    listOfNotNull(psiFacade.findPackage(fqName))
                } else {
                    DescriptorToSourceUtilsIde.getAllDeclarations(ref.expression.project, targetDescriptor, ref.expression.resolveScope)
                }
            }

            private fun getLabelTargets(ref: AbstractKtReference<KtElement>, context: BindingContext): Collection<PsiElement>? {
                val reference = ref.expression as? KtReferenceExpression ?: return null
                val labelTarget = context[BindingContext.LABEL_TARGET, reference]
                if (labelTarget != null) {
                    return listOf(labelTarget)
                }
                return context[BindingContext.AMBIGUOUS_LABEL_TARGET, reference]
            }

            override fun resolve(ref: AbstractKtReference<KtElement>, incompleteCode: Boolean): Array<ResolveResult> {
                val resolveToPsiElements = resolveToPsiElements(ref)
                return resolveToPsiElements.map { KotlinResolveResult(it) }.toTypedArray()
            }
        }

        val KOTLIN_RESOLVER = KotlinReferenceResolver()
    }
}

abstract class KtSimpleReference<T : KtReferenceExpression>(expression: T) : AbstractKtReference<T>(expression) {
    override fun getTargetDescriptors(context: BindingContext) = expression.getReferenceTargets(context)
}

abstract class KtMultiReference<T : KtElement>(expression: T) : AbstractKtReference<T>(expression)

/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copy
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.diagnostics.FirSimpleDiagnostic
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.impl.FirResolvedCallableReferenceImpl
import org.jetbrains.kotlin.fir.references.impl.FirResolvedNamedReferenceImpl
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.constructFunctionalTypeRef
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substituteOrNull
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.resultType
import org.jetbrains.kotlin.fir.resolve.withNullability
import org.jetbrains.kotlin.fir.scopes.impl.withReplacedConeType
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.impl.FirErrorTypeRefImpl
import org.jetbrains.kotlin.fir.types.impl.FirResolvedTypeRefImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeProjectionWithVarianceImpl
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.types.AbstractTypeApproximator
import org.jetbrains.kotlin.types.TypeApproximatorConfiguration
import org.jetbrains.kotlin.types.Variance

class FirCallCompletionResultsWriterTransformer(
    override val session: FirSession,
    private val finalSubstitutor: ConeSubstitutor,
    private val typeCalculator: ReturnTypeCalculator,
    private val typeApproximator: AbstractTypeApproximator
) : FirAbstractTreeTransformer<Nothing?>(phase = FirResolvePhase.IMPLICIT_TYPES_BODY_RESOLVE) {

    override fun transformQualifiedAccessExpression(
        qualifiedAccessExpression: FirQualifiedAccessExpression,
        data: Nothing?
    ): CompositeTransformResult<FirStatement> {
        val calleeReference =
            qualifiedAccessExpression.calleeReference as? FirNamedReferenceWithCandidate ?: return qualifiedAccessExpression.compose()

        val candidateFir = calleeReference.candidateSymbol.phasedFir
        val typeRef = (candidateFir as? FirTypedDeclaration)?.let {
            typeCalculator.tryCalculateReturnType(it)
        } ?: FirErrorTypeRefImpl(
            calleeReference.source,
            FirSimpleDiagnostic("Callee reference to candidate without return type: ${candidateFir.render()}")
        )

        qualifiedAccessExpression.replaceTypeRefWithSubstituted(calleeReference, typeRef)

        return qualifiedAccessExpression.transformCalleeReference(
            StoreCalleeReference,
            FirResolvedNamedReferenceImpl(
                calleeReference.source,
                calleeReference.name,
                calleeReference.candidateSymbol
            )
        ).compose()
    }

    private fun <D : FirExpression> D.replaceTypeRefWithSubstituted(
        calleeReference: FirNamedReferenceWithCandidate,
        typeRef: FirResolvedTypeRef
    ): D {
        val resultTypeRef = typeRef.substituteTypeRef(calleeReference.candidate)
        replaceTypeRef(resultTypeRef)
        return this
    }

    private fun FirResolvedTypeRef.substituteTypeRef(
        candidate: Candidate
    ): FirResolvedTypeRef {
        val initialType = candidate.substitutor.substituteOrNull(type)
        val finalType = finalSubstitutor.substituteOrNull(initialType)?.let { substitutedType ->
            typeApproximator.approximateToSuperType(
                substitutedType, TypeApproximatorConfiguration.FinalApproximationAfterResolutionAndInference
            ) as ConeKotlinType? ?: substitutedType
        }

        return withReplacedConeType(finalType)
    }

    override fun transformCallableReferenceAccess(
        callableReferenceAccess: FirCallableReferenceAccess,
        data: Nothing?
    ): CompositeTransformResult<FirStatement> {

        val calleeReference =
            callableReferenceAccess.calleeReference as? FirNamedReferenceWithCandidate ?: return callableReferenceAccess.compose()

        val typeRef = callableReferenceAccess.typeRef as FirResolvedTypeRef

        val initialType = calleeReference.candidate.substitutor.substituteOrSelf(typeRef.type)
        val finalType = finalSubstitutor.substituteOrSelf(initialType)

        val resultType = typeRef.withReplacedConeType(finalType)
        callableReferenceAccess.replaceTypeRef(resultType)

        return callableReferenceAccess.transformCalleeReference(
            StoreCalleeReference,
            FirResolvedCallableReferenceImpl(
                calleeReference.source,
                calleeReference.name,
                calleeReference.candidateSymbol
            ).apply {
                inferredTypeArguments.addAll(computeTypeArguments(calleeReference.candidate))
            }
        ).compose()
    }

    override fun transformVariableAssignment(
        variableAssignment: FirVariableAssignment,
        data: Nothing?
    ): CompositeTransformResult<FirStatement> {
        val calleeReference = variableAssignment.calleeReference as? FirNamedReferenceWithCandidate
            ?: return variableAssignment.compose()
        return variableAssignment.transformCalleeReference(
            StoreCalleeReference,
            FirResolvedNamedReferenceImpl(
                calleeReference.source,
                calleeReference.name,
                calleeReference.candidateSymbol
            )
        ).compose()
    }

    override fun transformFunctionCall(functionCall: FirFunctionCall, data: Nothing?): CompositeTransformResult<FirStatement> {
        val calleeReference = functionCall.calleeReference as? FirNamedReferenceWithCandidate ?: return functionCall.compose()
        val functionCall = functionCall.transformArguments(this, data) as FirFunctionCall

        val subCandidate = calleeReference.candidate
        val declaration = subCandidate.symbol.phasedFir as FirCallableMemberDeclaration<*>
        val typeArguments = computeTypeArguments(subCandidate)
            .mapIndexed { index, type ->
                when (val argument = functionCall.typeArguments.getOrNull(index)) {
                    is FirTypeProjectionWithVariance -> {
                        val typeRef = argument.typeRef as FirResolvedTypeRef
                        FirTypeProjectionWithVarianceImpl(
                            argument.source,
                            typeRef.withReplacedConeType(type),
                            argument.variance
                        )
                    }
                    else -> {
                        FirTypeProjectionWithVarianceImpl(
                            argument?.source,
                            FirResolvedTypeRefImpl(null, type),
                            Variance.INVARIANT
                        )
                    }
                }
            }

        val typeRef = typeCalculator.tryCalculateReturnType(declaration).let {
            if (functionCall.safe) {
                val nullableType = it.coneTypeUnsafe<ConeKotlinType>().withNullability(ConeNullability.NULLABLE)
                it.withReplacedConeType(nullableType)
            } else {
                it
            }
        }

        val resultType = typeRef.substituteTypeRef(subCandidate)

        return functionCall.copy(
            resultType = resultType,
            typeArguments = typeArguments,
            calleeReference = FirResolvedNamedReferenceImpl(
                calleeReference.source,
                calleeReference.name,
                calleeReference.candidateSymbol
            ),
            dispatchReceiver = subCandidate.dispatchReceiverExpression(),
            extensionReceiver = subCandidate.extensionReceiverExpression()
        ).compose()

    }

    private fun computeTypeArguments(
        candidate: Candidate
    ): List<ConeKotlinType> {
        val declaration = candidate.symbol.phasedFir as? FirCallableMemberDeclaration<*> ?: return emptyList()

        return declaration.typeParameters.map { ConeTypeParameterTypeImpl(it.symbol.toLookupTag(), false) }
            .map { candidate.substitutor.substituteOrSelf(it) }
            .map { finalSubstitutor.substituteOrSelf(it) }
    }

    override fun transformAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        data: Nothing?
    ): CompositeTransformResult<FirStatement> {
        val initialType = anonymousFunction.returnTypeRef.coneTypeSafe<ConeKotlinType>()
        if (initialType != null) {
            val finalType = finalSubstitutor.substituteOrNull(initialType)

            val resultType = anonymousFunction.returnTypeRef.withReplacedConeType(finalType)

            anonymousFunction.transformReturnTypeRef(StoreType, resultType)

            anonymousFunction.replaceTypeRef(anonymousFunction.constructFunctionalTypeRef(session))
        }
        return transformElement(anonymousFunction, data)
    }

    override fun transformBlock(block: FirBlock, data: Nothing?): CompositeTransformResult<FirStatement> {
        val initialType = block.resultType.coneTypeSafe<ConeKotlinType>()
        if (initialType != null) {
            val finalType = finalSubstitutor.substituteOrNull(initialType)
            val resultType = block.resultType.withReplacedConeType(finalType)
            block.replaceTypeRef(resultType)
        }
        return transformElement(block, data)
    }

    // Transformations for synthetic calls generated by FirSyntheticCallGenerator

    override fun transformWhenExpression(whenExpression: FirWhenExpression, data: Nothing?) = transformSyntheticCall(whenExpression)
    override fun transformTryExpression(tryExpression: FirTryExpression, data: Nothing?) = transformSyntheticCall(tryExpression)
    override fun transformCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall, data: Nothing?) = transformSyntheticCall(checkNotNullCall)

    private inline fun <reified D> transformSyntheticCall(syntheticCall: D): CompositeTransformResult<FirStatement>
            where D : FirResolvable, D : FirExpression {
        val syntheticCall = syntheticCall.transformChildren(this, null) as D
        val calleeReference = syntheticCall.calleeReference as? FirNamedReferenceWithCandidate ?: return syntheticCall.compose()

        val declaration = calleeReference.candidate.symbol.fir as? FirMemberFunction<*> ?: return syntheticCall.compose()

        val typeRef = typeCalculator.tryCalculateReturnType(declaration)
        syntheticCall.replaceTypeRefWithSubstituted(calleeReference, typeRef)

        return (syntheticCall.transformCalleeReference(
            StoreCalleeReference,
            FirResolvedNamedReferenceImpl(
                calleeReference.source,
                calleeReference.name,
                calleeReference.candidateSymbol
            )
        ) as D).compose()
    }
}

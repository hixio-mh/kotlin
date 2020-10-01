/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeRevisited
import org.jetbrains.kotlin.contracts.description.isDefinitelyVisited
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.isLateInit
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol

object FirPropertyInitializationAnalyzer : AbstractFirPropertyInitializationChecker() {
    override fun analyze(
        graph: ControlFlowGraph,
        reporter: DiagnosticReporter,
        data: Map<CFGNode<*>, PropertyInitializationInfo>,
        properties: Set<FirPropertySymbol>
    ) {
        val localData = data.filter {
            val symbolFir = (it.key.fir as? FirVariableSymbol<*>)?.fir
            symbolFir == null || symbolFir.initializer == null && symbolFir.delegate == null
        }

        val localProperties = properties.filter { it.fir.initializer == null && it.fir.delegate == null }.toSet()

        val reporterVisitor = UninitializedPropertyReporter(localData, localProperties, reporter)
        graph.traverse(TraverseDirection.Forward, reporterVisitor)
    }

    private class UninitializedPropertyReporter(
        val data: Map<CFGNode<*>, PropertyInitializationInfo>,
        val localProperties: Set<FirPropertySymbol>,
        val reporter: DiagnosticReporter
    ) : ControlFlowGraphVisitorVoid() {
        override fun visitNode(node: CFGNode<*>) {}

        private fun getPropertySymbol(node: CFGNode<*>): FirPropertySymbol? {
            val reference = (node.fir as? FirQualifiedAccess)?.calleeReference as? FirResolvedNamedReference ?: return null
            return reference.resolvedSymbol as? FirPropertySymbol
        }

        override fun visitVariableAssignmentNode(node: VariableAssignmentNode) {
            val symbol = getPropertySymbol(node) ?: return
            val kind = data.getValue(node)[symbol] ?: EventOccurrencesRange.ZERO
            if (symbol.fir.isVal && kind.canBeRevisited()) {
                node.fir.lValue.source?.let {
                    reporter.report(FirErrors.VAL_REASSIGNMENT.on(it, symbol))
                }
            }
        }

        override fun visitQualifiedAccessNode(node: QualifiedAccessNode) {
            val symbol = getPropertySymbol(node) ?: return
            if (symbol !in localProperties) return
            if (symbol.fir.isLateInit) return
            val kind = data.getValue(node)[symbol] ?: EventOccurrencesRange.ZERO
            if (!kind.isDefinitelyVisited()) {
                node.fir.source?.let {
                    reporter.report(FirErrors.UNINITIALIZED_VARIABLE.on(it, symbol))
                }
            }
        }
    }
}

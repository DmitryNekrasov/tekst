/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichPropertyReference
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm

class U8IrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Without the utf8-string library on the classpath there is nothing to rewrite.
        val symbols = U8Symbols.find(pluginContext) ?: return
        moduleFragment.transformChildrenVoid(U8CallTransformer(pluginContext, symbols))
    }
}

class U8Symbols(
    val u8Getter: IrSimpleFunctionSymbol,
    val u8Literal: IrSimpleFunctionSymbol,
    val u8LiteralLatin1: IrSimpleFunctionSymbol,
    val byteArrayOf: IrSimpleFunctionSymbol,
) {
    companion object {
        private val PACKAGE = FqName("utf8string")

        fun find(context: IrPluginContext): U8Symbols? {
            val finder = context.finderForBuiltins()
            val u8 = finder.findProperties(CallableId(PACKAGE, Name.identifier("u8"))).singleOrNull() ?: return null
            return U8Symbols(
                u8Getter = u8.owner.getter?.symbol ?: return null,
                u8Literal = finder.findFunctions(CallableId(PACKAGE, Name.identifier("u8Literal"))).singleOrNull() ?: return null,
                u8LiteralLatin1 = finder.findFunctions(CallableId(PACKAGE, Name.identifier("u8LiteralLatin1"))).singleOrNull()
                    ?: return null,
                byteArrayOf = finder.findFunctions(CallableId(FqName("kotlin"), Name.identifier("byteArrayOf"))).single(),
            )
        }
    }
}

// On klib targets, byteArrayOf becomes static data, but every byte also costs about 15-20 bytes of serialized IR.
private const val MAX_BYTE_ARRAY_OF_SIZE = 1024

class U8CallTransformer(
    private val context: IrPluginContext,
    private val symbols: U8Symbols,
) : IrElementTransformerVoidWithContext() {
    private val isJvm = context.platform.isJvm()

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (expression.symbol != symbols.u8Getter) return expression
        val folded = expression.arguments[0]?.foldToString()
        if (folded == null) {
            report(expression, U8Diagnostics.U8_NOT_CONSTANT)
            return expression
        }
        val literal = encodeUtf8(folded.value)
        if (literal.hasUnpairedSurrogate) report(expression, U8Diagnostics.U8_UNPAIRED_SURROGATE)
        val replacement = buildLiteral(literal, expression.startOffset, expression.endOffset)
        if (folded.sideEffects.isEmpty()) return replacement
        return IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null).apply {
            statements += folded.sideEffects
            statements += replacement
        }
    }

    // A reference such as String::u8 calls the getter on a parameter, not on a constant; only bound values can be constant.
    override fun visitRichPropertyReference(expression: IrRichPropertyReference): IrExpression {
        for (i in expression.boundValues.indices) expression.boundValues[i] = expression.boundValues[i].transform(this, null)
        return expression
    }

    override fun visitRichFunctionReference(expression: IrRichFunctionReference): IrExpression {
        for (i in expression.boundValues.indices) expression.boundValues[i] = expression.boundValues[i].transform(this, null)
        return expression
    }

    private fun buildLiteral(literal: Utf8Literal, startOffset: Int, endOffset: Int): IrExpression {
        val builtIns = context.irBuiltIns
        val codePointCount = IrConstImpl.int(startOffset, endOffset, builtIns.intType, literal.codePointCount)
        if (isJvm || literal.bytes.size > MAX_BYTE_ARRAY_OF_SIZE) {
            return call(symbols.u8LiteralLatin1, startOffset, endOffset).apply {
                arguments[0] = IrConstImpl.string(startOffset, endOffset, builtIns.stringType, latin1String(literal.bytes))
                arguments[1] = codePointCount
            }
        }
        val bytes = call(symbols.byteArrayOf, startOffset, endOffset).apply {
            arguments[0] = IrVarargImpl(
                startOffset,
                endOffset,
                builtIns.byteArray.defaultType,
                builtIns.byteType,
                literal.bytes.map { IrConstImpl.byte(startOffset, endOffset, builtIns.byteType, it) },
            )
        }
        return call(symbols.u8Literal, startOffset, endOffset).apply {
            arguments[0] = bytes
            arguments[1] = codePointCount
        }
    }

    private fun call(symbol: IrSimpleFunctionSymbol, startOffset: Int, endOffset: Int): IrCall =
        IrCallImpl(startOffset, endOffset, symbol.owner.returnType, symbol)

    private fun report(expression: IrCall, factory: KtDiagnosticFactory0) {
        context.diagnosticReporter.at(expression, currentFile).report(factory)
    }
}

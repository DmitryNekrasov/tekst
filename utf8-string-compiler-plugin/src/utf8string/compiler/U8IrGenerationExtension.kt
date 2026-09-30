/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichPropertyReference
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
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
    private var fileLiterals: FileLiterals? = null

    override fun visitFileNew(declaration: IrFile): IrFile {
        val literals = if (declaration.declarations.any { it is IrScript }) null else FileLiterals(declaration)
        fileLiterals = literals
        val file = super.visitFileNew(declaration)
        fileLiterals = null
        literals?.holder?.let(file::addChild)
        return file
    }

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
        val literals = fileLiterals
        val replacement = if (literals == null || isInInlineFunction()) {
            buildLiteral(literal, expression.startOffset, expression.endOffset)
        } else {
            literals.get(literal, expression.startOffset, expression.endOffset)
        }
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

    // The body of an inline function is copied into other files and modules, which cannot reach the file's private holder.
    private fun isInInlineFunction(): Boolean = allScopes.any { (it.irElement as? IrFunction)?.isInline == true }

    // Each distinct literal of a file is created once, in a private object of that file. The object has its own
    // initialization, so using a literal does not initialize the other top-level properties of the file.
    private inner class FileLiterals(private val file: IrFile) {
        var holder: IrClass? = null
            private set
        private val getters = HashMap<String, IrSimpleFunction>()

        fun get(literal: Utf8Literal, startOffset: Int, endOffset: Int): IrExpression {
            val getter = getters.getOrPut(latin1String(literal.bytes)) { addLiteral(literal, startOffset, endOffset) }
            val holder = holder!!
            return IrCallImpl(startOffset, endOffset, getter.returnType, getter.symbol).apply {
                arguments[0] = IrGetObjectValueImpl(startOffset, endOffset, holder.symbol.defaultType, holder.symbol)
            }
        }

        private fun addLiteral(literal: Utf8Literal, startOffset: Int, endOffset: Int): IrSimpleFunction {
            val holder = holder ?: createHolder().also { holder = it }
            val name = Name.identifier("literal${getters.size}")
            val type = symbols.u8Getter.owner.returnType
            val property = holder.addProperty {
                this.name = name
                visibility = DescriptorVisibilities.INTERNAL
            }
            // Static on the JVM, like the fields of a Kotlin object: a static final field is a constant for the JIT.
            val field = context.irFactory.buildField {
                this.startOffset = SYNTHETIC_OFFSET
                this.endOffset = SYNTHETIC_OFFSET
                this.name = name
                this.type = type
                visibility = DescriptorVisibilities.PRIVATE
                isFinal = true
                isStatic = isJvm
            }
            field.parent = holder
            field.correspondingPropertySymbol = property.symbol
            field.initializer = DeclarationIrBuilder(context, holder.symbol)
                .irExprBody(buildLiteral(literal, startOffset, endOffset))
            property.backingField = field
            return property.addGetter {
                returnType = type
                visibility = DescriptorVisibilities.INTERNAL
                origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
            }.also { getter ->
                getter.parent = holder
                val receiver = holder.thisReceiver!!.copyTo(getter)
                getter.parameters += receiver
                getter.body = DeclarationIrBuilder(context, getter.symbol).irBlockBody {
                    +irReturn(irGetField(if (isJvm) null else irGet(receiver), field))
                }
            }
        }

        private fun createHolder(): IrClass = context.irFactory.buildClass {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            kind = ClassKind.OBJECT
            visibility = DescriptorVisibilities.PRIVATE
            val fileName = file.fileEntry.name.substringAfterLast('/').substringAfterLast('\\')
            name = Name.identifier("U8Literals\$" + PackagePartClassUtils.getFilePartShortName(fileName))
        }.also { holder ->
            holder.parent = file
            holder.createThisReceiverParameter()
            holder.addConstructor {
                isPrimary = true
                visibility = DescriptorVisibilities.PRIVATE
            }.body = DeclarationIrBuilder(context, holder.symbol).irBlockBody {
                +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.primaryConstructor!!)
                +IrInstanceInitializerCallImpl(startOffset, endOffset, holder.symbol, context.irBuiltIns.unitType)
            }
        }
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

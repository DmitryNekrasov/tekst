/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullableString
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class FoldedString(val value: String, val sideEffects: List<IrStatement>)

// Only `const val` reads are inlined before IR plugins run, so templates, `+` and trimIndent() are folded here.
fun IrExpression.foldToString(): FoldedString? {
    val sideEffects = mutableListOf<IrStatement>()
    val value = fold(this, sideEffects) ?: return null
    return FoldedString(value, sideEffects)
}

private val STRING_PLUS = CallableId(ClassId(FqName("kotlin"), Name.identifier("String")), Name.identifier("plus"))
private val NULLABLE_STRING_PLUS = CallableId(FqName("kotlin"), Name.identifier("plus"))
private val CHAR_PLUS_STRING = CallableId(FqName("kotlin.text"), Name.identifier("plus"))
private val TRIM_INDENT = CallableId(FqName("kotlin.text"), Name.identifier("trimIndent"))
private val TRIM_MARGIN = CallableId(FqName("kotlin.text"), Name.identifier("trimMargin"))
private val FOLDABLE_CALL_NAMES = setOf(STRING_PLUS.callableName, TRIM_INDENT.callableName, TRIM_MARGIN.callableName)

private fun fold(expression: IrExpression, sideEffects: MutableList<IrStatement>): String? = when (expression) {
    is IrConst -> constantText(expression)
    is IrStringConcatenation -> buildString {
        for (argument in expression.arguments) append(fold(argument, sideEffects) ?: return null)
    }
    // A `const val` read through a receiver that has to be evaluated.
    is IrComposite -> {
        val last = expression.statements.lastOrNull() as? IrExpression
        if (last == null) {
            null
        } else {
            sideEffects += expression.statements.dropLast(1)
            fold(last, sideEffects)
        }
    }
    is IrCall -> foldCall(expression, sideEffects)
    else -> null
}

private fun constantText(constant: IrConst): String? {
    val type = constant.type
    return when {
        constant.kind == IrConstKind.Null -> "null"
        type.isString() || type.isNullableString() -> constant.value as String
        type.isChar() -> (constant.value as Char).toString()
        type.isBoolean() || type.isByte() || type.isShort() || type.isInt() || type.isLong() -> constant.value.toString()
        // Unsigned constants are stored as signed values, and Float and Double are printed differently on JS.
        else -> null
    }
}

private fun foldCall(call: IrCall, sideEffects: MutableList<IrStatement>): String? {
    val function = call.symbol.owner
    if (function.name !in FOLDABLE_CALL_NAMES) return null
    return when (function.callableId) {
        STRING_PLUS, NULLABLE_STRING_PLUS, CHAR_PLUS_STRING -> {
            val left = call.arguments[0]?.let { fold(it, sideEffects) } ?: return null
            val right = call.arguments[1]?.let { fold(it, sideEffects) } ?: return null
            left + right
        }
        TRIM_INDENT -> call.arguments[0]?.let { fold(it, sideEffects) }?.trimIndent()
        TRIM_MARGIN -> {
            val receiver = call.arguments[0]?.let { fold(it, sideEffects) } ?: return null
            val marginPrefix = call.arguments[1]?.let { fold(it, sideEffects) ?: return null } ?: "|"
            // trimMargin throws for a blank prefix; leave that to run time.
            if (marginPrefix.isBlank()) null else receiver.trimMargin(marginPrefix)
        }
        else -> null
    }
}

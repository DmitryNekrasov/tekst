/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

/**
 * The UTF-8 encoding of this string. The utf8-string compiler plugin encodes a constant string at compile time,
 * and gives the `U8_NOT_CONSTANT` warning for any other string.
 */
public val String.u8: Utf8String
    get() = Utf8String.fromString(this)

/** The UTF-8 encoding of this string at run time, with no `U8_NOT_CONSTANT` warning, unlike [u8]. */
public fun String.toUtf8String(): Utf8String = Utf8String.fromString(this)

// The compiler plugin generates calls to these two functions, so their signatures must not change. On the JVM, the
// calls also name the utf8string.U8Kt class, so the functions must stay in this file, and the file must not get
// @file:JvmName.
@PublishedApi
internal fun u8Literal(bytes: ByteArray, codePointCount: Int): Utf8String = Utf8String(bytes, codePointCount)

@PublishedApi
internal fun u8LiteralLatin1(latin1: String, codePointCount: Int): Utf8String =
    Utf8String(latin1Bytes(latin1), codePointCount)

internal expect fun latin1Bytes(latin1: String): ByteArray

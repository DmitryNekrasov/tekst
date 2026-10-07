/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.psi.KtElement

object U8Diagnostics : KtDiagnosticsContainer() {
    val U8_NOT_CONSTANT by warning0<KtElement>()
    val U8_UNPAIRED_SURROGATE by warning0<KtElement>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = U8DiagnosticRenderers
}

object U8DiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("utf8-string") { map ->
        map.put(
            U8Diagnostics.U8_NOT_CONSTANT,
            "The receiver of 'u8' is not a compile-time constant, so it is encoded at run time.",
        )
        map.put(
            U8Diagnostics.U8_UNPAIRED_SURROGATE,
            "The 'u8' literal contains an unpaired surrogate, which is encoded as U+FFFD.",
        )
    }
}

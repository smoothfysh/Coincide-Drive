package com.coincide.drive.data.preferences

import com.coincide.drive.BuildConfig
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline Cadence-X license code verifier and generator for Coincide Drive.
 *
 * Format: CADX-YYYY-YYYY
 * Derived deterministically from the user's email address via HMAC-SHA256
 * using the git-ignored secret key injected from `local.properties`.
 */
object LicenseManager {

    const val FREE_MAX_SYNC_PAIRS = 1
    const val PRO_MAX_SYNC_PAIRS = 5
    const val CODE_PREFIX = "CADX"

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // 32 unambiguous chars

    /**
     * Generates the expected `CADX-YYYY-YYYY` unlock code for the given [email].
     */
    fun generateCodeForEmail(email: String): String {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        require(normalizedEmail.isNotEmpty()) { "Email must not be blank" }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKeySpec = SecretKeySpec(BuildConfig.LICENSE_SECRET_KEY.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKeySpec)
        val digest = mac.doFinal(normalizedEmail.toByteArray(Charsets.UTF_8))

        val chars = CharArray(8)
        for (i in 0 until 8) {
            val index = (digest[i].toInt() and 0xFF) and 0x1F
            chars[i] = ALPHABET[index]
        }

        val part1 = String(chars, 0, 4)
        val part2 = String(chars, 4, 4)
        return "$CODE_PREFIX-$part1-$part2"
    }

    /**
     * Verifies whether [inputCode] is a valid `CADX-YYYY-YYYY` unlock code for [email].
     * Also checks against any of [candidateEmails] (e.g. currently linked Google accounts)
     * if [email] is blank or doesn't match directly.
     *
     * @return The matched email address if valid, or `null` if the code is invalid.
     */
    fun verifyCode(
        inputCode: String,
        primaryEmail: String,
        candidateEmails: List<String> = emptyList()
    ): String? {
        val cleanedInput = normalizeCodeInput(inputCode)
        if (cleanedInput.length != 12 || !cleanedInput.startsWith(CODE_PREFIX)) {
            return null
        }

        val emailsToCheck = buildList {
            if (primaryEmail.isNotBlank()) add(primaryEmail.trim())
            candidateEmails.forEach { candidate ->
                if (candidate.isNotBlank() && none { it.equals(candidate.trim(), ignoreCase = true) }) {
                    add(candidate.trim())
                }
            }
        }

        for (email in emailsToCheck) {
            val expected = normalizeCodeInput(generateCodeForEmail(email))
            if (cleanedInput == expected) {
                return email.lowercase(Locale.ROOT)
            }
        }

        return null
    }

    /**
     * Formats raw user input into `CADX-YYYY-YYYY` display format.
     */
    fun normalizeCodeInput(raw: String): String {
        return raw.trim()
            .uppercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")
    }
}

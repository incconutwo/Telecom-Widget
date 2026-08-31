package com.telecom.widget.security

import com.telecom.widget.network.SavedAccount
import org.junit.Assert.*
import org.junit.Test

class CryptoManagerTest {

    @Test
    fun testEncryptAndDecrypt() {
        val original = "MyStrongPassword#2026!"
        val encrypted = CryptoManager.encrypt(original)

        assertTrue("Encrypted text must start with enc: prefix", encrypted.startsWith("enc:"))
        assertNotEquals("Encrypted text must differ from plaintext", original, encrypted)

        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals("Decrypted text must match original plaintext", original, decrypted)
    }

    @Test
    fun testRandomizedInitializationVector() {
        val password = "samePasswordEveryTime"
        val cipher1 = CryptoManager.encrypt(password)
        val cipher2 = CryptoManager.encrypt(password)

        assertNotEquals("Subsequent encryptions of same input must produce distinct ciphertexts due to random IV", cipher1, cipher2)
        assertEquals(password, CryptoManager.decrypt(cipher1))
        assertEquals(password, CryptoManager.decrypt(cipher2))
    }

    @Test
    fun testIdempotence() {
        val original = "SensitiveToken123"
        val encryptedOnce = CryptoManager.encrypt(original)
        val encryptedTwice = CryptoManager.encrypt(encryptedOnce)

        assertEquals("Re-encrypting an already encrypted string should be a no-op", encryptedOnce, encryptedTwice)
        assertEquals(original, CryptoManager.decrypt(encryptedTwice))
    }

    @Test
    fun testLegacyPlaintextPassthrough() {
        val legacyPlaintext = "unencrypted_legacy_password"
        val decrypted = CryptoManager.decrypt(legacyPlaintext)

        assertEquals("Legacy plaintext without enc: prefix should pass through cleanly", legacyPlaintext, decrypted)
    }

    @Test
    fun testEmptyStrings() {
        assertEquals("", CryptoManager.encrypt(""))
        assertEquals("", CryptoManager.decrypt(""))
    }

    @Test
    fun testSavedAccountExtensions() {
        val account = SavedAccount(
            operator = "Orange",
            phone = "0612345678",
            password = "SecretPassword123"
        )

        val encryptedAccount = account.toEncrypted()
        assertTrue(encryptedAccount.password.startsWith("enc:"))
        assertEquals("Orange", encryptedAccount.operator)
        assertEquals("0612345678", encryptedAccount.phone)

        val decryptedAccount = encryptedAccount.toDecrypted()
        assertEquals("SecretPassword123", decryptedAccount.password)
    }

    @Test
    fun testListExtensions() {
        val accounts = listOf(
            SavedAccount(operator = "Maroc Telecom", email = "test@iam.ma", password = "pass1"),
            SavedAccount(operator = "Orange", phone = "0600000000", password = "pass2"),
            SavedAccount(operator = "Inwi", phone = "0700000000", password = "pass3")
        )

        val encryptedList = accounts.toEncryptedForStorage()
        assertEquals(3, encryptedList.size)
        assertTrue(encryptedList.all { it.password.startsWith("enc:") })

        val decryptedList = encryptedList.toDecryptedFromStorage()
        assertEquals("pass1", decryptedList[0].password)
        assertEquals("pass2", decryptedList[1].password)
        assertEquals("pass3", decryptedList[2].password)
    }
}

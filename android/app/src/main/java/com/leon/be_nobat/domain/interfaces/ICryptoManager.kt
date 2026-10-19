package com.leon.be_nobat.domain.interfaces


interface ICryptoManager {

    fun encrypt(threshold: String): String

    fun decrypt(encryptedData: String): String
}
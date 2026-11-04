package com.wavetone.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal platform key-value storage (SharedPreferences / NSUserDefaults / in-memory). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

expect fun createPlatformStore(): KeyValueStore

class ContactVault(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val _contacts = MutableStateFlow(load())
    val contacts: StateFlow<List<PairedContact>> = _contacts.asStateFlow()

    private fun load(): List<PairedContact> {
        val raw = store.get(KEY_CONTACTS) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(PairedContact.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist() {
        store.put(KEY_CONTACTS, json.encodeToString(ListSerializer(PairedContact.serializer()), _contacts.value))
    }

    fun add(contact: PairedContact) {
        _contacts.value = _contacts.value.filter { it.phoneNumber != contact.phoneNumber } + contact
        persist()
    }

    fun remove(phoneNumber: String) {
        _contacts.value = _contacts.value.filter { it.phoneNumber != phoneNumber }
        persist()
    }

    fun find(phoneNumber: String): PairedContact? =
        _contacts.value.firstOrNull { it.phoneNumber == normalizePhone(phoneNumber) }

    fun myPhone(): String = store.get(KEY_MY_PHONE) ?: DEFAULT_MY_PHONE

    fun setMyPhone(phone: String) {
        store.put(KEY_MY_PHONE, normalizePhone(phone))
    }

    companion object {
        const val KEY_CONTACTS = "wavetone_contacts_json"
        const val KEY_MY_PHONE = "wavetone_my_phone"
        const val DEFAULT_MY_PHONE = "919999999999"
    }
}

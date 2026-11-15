package com.notipass.droid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ContactUtil {

    private const val TAG = "ContactUtil"

    fun getContactName(context: Context, phoneNumber: String): String? {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return null

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.d(TAG, "READ_CONTACTS permission not granted, skipping contact lookup")
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleanNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve contact name for $cleanNumber", e)
            null
        }
    }

    fun getPhoneNumberByName(context: Context, name: String): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} = ?"
            val selectionArgs = arrayOf(cleanName, cleanName)

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIndex != -1) cursor.getString(numIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve phone number for $cleanName", e)
            null
        }
    }
}

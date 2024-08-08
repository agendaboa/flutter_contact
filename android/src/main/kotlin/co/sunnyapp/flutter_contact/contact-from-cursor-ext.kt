@file:Suppress("NAME_SHADOWING", "FunctionName")

package co.sunnyapp.flutter_contact

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Build
import android.provider.ContactsContract.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

interface ContactExtensions {

    val resolver: ContentResolver
    val mode: ContactMode

    fun ContactKeys(id: Any) = contactKeyOf(mode = mode, value = id)
            ?: badParameter("{}", "identifier")

    @SuppressLint("Recycle")
    fun ContentResolver.queryContacts(query: String? = null, sortBy: String? = null,
                                      forCount: Boolean = false): Cursor? {

        var selectionArgs = when (forCount) {
            true -> arrayOf()
            false -> arrayOf(
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE

            )
        }
        var selection = selectionArgs.joinToString(separator = " OR ") { "${Data.MIMETYPE}=?" }

        val projections = if (forCount) mode.projectionsIdsOnly else mode.projections
        if (query != null) {
            selectionArgs = arrayOf("$query%")
            selection = "${Data.DISPLAY_NAME_PRIMARY} LIKE ?"
        }

        val sortOrder = if (forCount) null else ContactSorting[sortBy]

        return query(Data.CONTENT_URI, projections, selection, selectionArgs, sortOrder + ContactSorting.byUnifiedContact)
    }

    /**
     * Creates a cursor for a contact or raw_contact, ensuring to sort it correctly
     */
    fun ContentResolver.findContactById(keys: ContactKeys): Cursor? {
        return query(Data.CONTENT_URI, mode.projections, keys.toQuery(), keys.params, "${ContactSorting.byUnifiedContact}")
    }

    /**
     * Builds the list of contacts from the cursor
     * @param cursor
     * @return the list of contacts
     */
    fun Cursor?.toContactList(mode: ContactMode, limit: Int, offset: Int): List<Contact> {
        val cursor = this ?: return emptyList()

        val contactsById = mutableMapOf<String, Contact>()

        if (offset > 0) {
            val skipped = mutableSetOf<String>()
            while (skipped.size <= offset && cursor.moveToNext()) {
                val columnIndex = cursor.getColumnIndex(mode.contactIdRef)
                skipped += cursor.getString(columnIndex)
            }

            /// We overshoot because one record could involve multiple rows.
            if(skipped.size > offset) {
                skipped.remove(skipped.last())
                // move the cursor to previous position as we don't want to skip this contact row
                cursor.moveToPrevious()
            }
        }

        try {
            while (cursor.moveToNext() && contactsById.size <= limit) {
                val columnIndex = cursor.getColumnIndex(mode.contactIdRef)
                val contactId = cursor.getString(columnIndex)

                val unifiedContactId = cursor.long(CommonDataKinds.Identity.CONTACT_ID)
                val rawContactId = cursor.long(CommonDataKinds.Identity.RAW_CONTACT_ID)

                val contact = when (val existing = contactsById[contactId]) {
                    null -> Contact(keys = ContactKeys(contactId.toLong())).also {
                            contactsById[contactId] = it
                        }
                    else -> existing
                }

                val isPrimary = when (mode) {
                    ContactMode.UNIFIED -> rawContactId == unifiedContactId
                    ContactMode.SINGLE -> true
                }

                rawContactId?.also {
                    contact.singleContactId = it
                }
                unifiedContactId?.also {
                    contact.unifiedContactId = it
                }

                cursor.string(CommonDataKinds.Identity.LOOKUP_KEY)?.also {
                    contact.lookupKey = it
                }

                val mimeType = cursor.string(Data.MIMETYPE)


                // set the display name as primary and outside the mime type check.
                // if the raw contact has no name then there will be no mime type about name data,
                // so display name will be null.
                contact.displayName = cursor.string(Contacts.DISPLAY_NAME)

                //NAMES
                when (mimeType) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        contact.givenName = contact.givenName
                                ?: cursor.string(CommonDataKinds.StructuredName.GIVEN_NAME)
                        contact.middleName = contact.middleName
                                ?: cursor.string(CommonDataKinds.StructuredName.MIDDLE_NAME)
                        contact.familyName = contact.familyName
                                ?: cursor.string(CommonDataKinds.StructuredName.FAMILY_NAME)
                        contact.prefix = contact.prefix
                                ?: cursor.string(CommonDataKinds.StructuredName.PREFIX)
                        contact.suffix = contact.suffix
                                ?: cursor.string(CommonDataKinds.StructuredName.SUFFIX)
                    }
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        cursor.string(CommonDataKinds.Phone.NUMBER)?.also { phone ->
                            contact.phones += Item(label = cursor.getPhoneLabel(), value = phone)
                        }
                    }

                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        cursor.string(CommonDataKinds.Email.ADDRESS)?.also { email ->
                            contact.emails += Item(label = cursor.getEmailLabel(), value = email)
                        }
                    }

                }
            }
        } finally {
            if (!isClosed) {
                close();
            }
        }

        /// One logical record involves multiple rows, so we load up to one extra, and then drop it
        if (contactsById.size > limit) {
            contactsById.remove(contactsById.entries.last().key)
        }
        return contactsById.values.toList()
    }


    fun getAvatarDataForContactIfAvailable(contactKeys: ContactKeys, highRes: Boolean = true): ByteArray? {
        val stream = resolver.openContactPhotoInputStream(contactKeys, highRes) ?: return null
        return stream.use { input ->
            val stream = ByteArrayOutputStream()

            val bitmap = android.graphics.BitmapFactory.decodeStream(input)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)

            stream.toByteArray()
        }
    }

    fun Contact.setAvatarDataForContactIfAvailable(highRes: Boolean) {
        val keys = keys?.checkValid() ?: return
        val bytes = getAvatarDataForContactIfAvailable(keys, highRes = highRes) ?: return
        this.avatar = bytes
    }
}

/**
 * Copied from ContactsContract and adapted for RawContact
 */
fun ContentResolver.openContactPhotoInputStream(key: ContactKeys, preferHighres: Boolean): InputStream? {

    if (key.mode == ContactMode.UNIFIED) return Contacts.openContactPhotoInputStream(this, key.contactUri, preferHighres)

    if (preferHighres) {
        try {
            return openAssetFileDescriptor(key.photoUri, "r")?.createInputStream()
        } catch (e: IOException) {
            // continue to the next block
        }
    }

    // FIND a data record where mimetype=PHOTO that matches the ID
    return query(Data.CONTENT_URI, arrayOf(Data.DATA15), "(${key.toQuery()}) AND ${Data.MIMETYPE} = ?",
            arrayOf(key.identifier.toString(), CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null)
            ?.use { cursor ->
                when (cursor.moveToNext()) {
                    false -> null
                    true -> cursor.toInputStream()
                }
            }
}


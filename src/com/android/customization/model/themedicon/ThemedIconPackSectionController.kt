/*
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.model.themedicon

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope

import com.android.customization.picker.themedicon.ThemedIconPackPickerFragment
import com.android.customization.picker.themedicon.ThemedIconPackSectionView
import com.android.customization.ResourceProxy
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThemedIconPackSectionController(
    private val context: Context,
    private val navigationController: CustomizationSectionNavigationController,
    private val themedIconSwitchProvider: ThemedIconSwitchProvider,
    private val lifecycleOwner: LifecycleOwner,
    savedInstanceState: Bundle?,
) : CustomizationSectionController<ThemedIconPackSectionView> {

    private val pm = context.packageManager
    private val mutex = Mutex()

    private val lifecycleScope: LifecycleCoroutineScope
        get() = lifecycleOwner.lifecycleScope

    private val themedIconUtils: ThemedIconUtils
        get() = themedIconSwitchProvider.themedIconUtils

    private var themedIconPackage: String? = null
    private var summaryView: TextView? = null

    init {
        val pkg = savedInstanceState?.getString(
            KEY_THEMED_ICON_PACK,
            null /* defaultValue */
        )
        if (pkg == null) {
            if (isAvailable(context)) {
                fetchThemedIconPack()
            }
        } else {
            themedIconPackage = pkg
        }
    }

    private fun fetchThemedIconPack() {
        val uri = themedIconUtils.getUriForPath(KEY_THEMED_ICON_PACK)
        lifecycleScope.launchWhenCreated {
            withContext(Dispatchers.Default) {
                val cursor = context.contentResolver.query(
                    uri,
                    null, /* projection*/
                    null, /* selection */
                    null, /* selectionArgs */
                    null, /* sortOrder */
                ) ?: return@withContext
                cursor.use {
                    it.moveToNext()
                    mutex.withLock {
                        themedIconPackage = it.getString(it.getColumnIndex(KEY_NAME))
                    }
                }
            }
        }
    }

    override fun isAvailable(context: Context): Boolean {
        return themedIconSwitchProvider.isThemedIconAvailable()
    }

    override fun createView(context: Context): ThemedIconPackSectionView {
        val view = LayoutInflater.from(context).inflate(
            ResourceProxy.Layout.themed_icon_pack_section_view,
            null /* root */
        ) as ThemedIconPackSectionView
        summaryView = view.findViewById(ResourceProxy.Id.themed_icon_pack_summary)
        lifecycleScope.launch {
            mutex.withLock {
                updateSummary(themedIconPackage)
            }
        }
        view.setOnClickListener {
            val pickerFragment = ThemedIconPackPickerFragment()
            pickerFragment.setOnAppSelectListener {
                mutex.withLock {
                    themedIconPackage = it
                }
                withContext(Dispatchers.Default) {
                    savePackage(it)
                }
                // Toggling it on and off will trigger updates. Hacky but rather easy.
                themedIconSwitchProvider.setThemedIconEnabled(false)
                themedIconSwitchProvider.setThemedIconEnabled(it != null)
            }
            pickerFragment.setInitalCheckedApp(themedIconPackage)
            navigationController.navigateTo(pickerFragment)
        }
        return view
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        lifecycleScope.launch {
            mutex.withLock {
                savedInstanceState.putString(KEY_THEMED_ICON_PACK, themedIconPackage)
            }
        }
    }

    private fun updateSummary(packageName: String?) {
        summaryView?.text = if (packageName != null) {
            val packageLabel = getLabelForPackageName(packageName)
            if (packageLabel != null) {
                "$packageLabel ($packageName)"
            } else {
                packageName
            }
        } else {
            context.getString(ResourceProxy.String.system_icons)
        }
    }

    private fun getLabelForPackageName(packageName: String): CharSequence? =
        try {
            pm.getApplicationInfo(packageName, 0 /* flags */).loadLabel(pm)
        } catch (e: NameNotFoundException) {
            null
        }

    private suspend fun savePackage(packageName: String?) {
        val values = ContentValues().apply {
            put(KEY_NAME, packageName)
        }
        val uri = themedIconUtils.getUriForPath(KEY_THEMED_ICON_PACK)
        val result = context.contentResolver.update(
            uri,
            values,
            null, /* where */
            null, /* selectionArgs */
        )
        if (result != RESULT_SUCCESS) {
            Log.e(TAG, "Failed to update themed icon pack")
        }
    }

    companion object {
        private const val TAG = "ThemedIconPackSectionController"

        private const val KEY_THEMED_ICON_PACK = "themed_icon_pack"
        private const val KEY_NAME = "name"

        private const val RESULT_SUCCESS = 1
    }
}

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

package com.android.customization.picker.themedicon

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView

import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.android.customization.ResourceProxy
import com.android.wallpaper.picker.AppbarFragment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemedIconPackPickerFragment : AppbarFragment() {

    private lateinit var pm: PackageManager
    private lateinit var adapter: AppListAdapter

    private var recyclerView: RecyclerView? = null
    private var progressBar: ContentLoadingProgressBar? = null
    private var appSelectListener: suspend CoroutineScope.(String?) -> Unit = {}
    private var initialCheckedApp: String? = null

    fun setOnAppSelectListener(listener: suspend CoroutineScope.(String?) -> Unit) {
        appSelectListener = listener
    }

    fun setInitalCheckedApp(checkedApp: String?) {
        initialCheckedApp = checkedApp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pm = requireContext().packageManager
    }

    override fun getDefaultTitle(): CharSequence = getString(ResourceProxy.String.themed_icon_title)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(
        ResourceProxy.Layout.themed_icons_app_list_layout,
        container,
        false /* attachToParent */
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setUpToolbar(view)

        // For nav bar edge-to-edge effect.
        view.setOnApplyWindowInsetsListener { v, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBarsInsets.top,
                v.paddingRight,
                systemBarsInsets.bottom
            )
            return@setOnApplyWindowInsetsListener WindowInsets.CONSUMED
        }

        adapter = AppListAdapter(requireActivity().layoutInflater).also {
            it.setCheckedItem(initialCheckedApp)
            it.setOnItemSelectListener {
                lifecycleScope.launch {
                    appSelectListener(it)
                }
            }
        }

        recyclerView = view.findViewById<RecyclerView>(ResourceProxy.Id.apps_list)?.also {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }

        progressBar = view.findViewById(ResourceProxy.Id.loading_progress)
        progressBar?.show()
        lifecycleScope.launch {
            refreshList()
        }
    }

    private suspend fun refreshList() {
        val list = withContext(Dispatchers.Default) {
            pm.getInstalledPackages(PackageManager.MATCH_ALL).filter {
                !it.applicationInfo.isSystemApp() && hasIconResourceMap(it)
            }.map {
                AppInfo(
                    it.packageName,
                    it.applicationInfo.loadLabel(pm).toString(),
                    it.applicationInfo.loadIcon(pm),
                )
            }.sortedBy {
                it.label
            }.toMutableList()
        }
        list.add(
            0 /* index */,
            AppInfo(
                null /* packageName */,
                getString(ResourceProxy.String.system_icons),
                pm.getDefaultActivityIcon()
            )
        )

        adapter.submitList(list.toList())
        progressBar?.hide()
    }

    private fun hasIconResourceMap(packageInfo: PackageInfo): Boolean {
        val res = try {
            pm.getResourcesForApplication(packageInfo.applicationInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: return false

        val resID = res.getIdentifier(
            "grayscale_icon_map",
            "xml",
            packageInfo.packageName
        )
        return resID != 0
    }

    private class AppListAdapter(
        private val layoutInflater: LayoutInflater
    ) : ListAdapter<AppInfo, AppListViewHolder>(itemCallback) {

        // Package name of the checked item
        private var checkedItem: String? = null
        // Index of the checked item
        private var checkedItemIndex = -1
        private var itemSelectListener: (String?) -> Unit = {}

        /**
         * @param item package name of the checked item
         */
        fun setCheckedItem(item: String?) {
            checkedItem = item
            updateCheckedItemIndex(currentList)
        }

        private fun updateCheckedItemIndex(list: List<AppInfo>?) {
            if (list == null) {
                checkedItemIndex = -1
                return
            }
            checkedItemIndex = if (checkedItem == null) {
                0
            } else list.indexOfFirst {
                it.packageName == checkedItem
            }
        }

        /**
         * Lambda parameter will be package name of the checked item
         */
        fun setOnItemSelectListener(listener: (String?) -> Unit) {
            itemSelectListener = listener
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ) = AppListViewHolder(
                layoutInflater.inflate(
                    ResourceProxy.Layout.themed_icons_app_list_item,
                    parent,
                    false /* attachToParent */
                )
            )

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            val item = getItem(position)

            holder.label.text = item.label
            holder.packageName.visibility = if (item.packageName == null) View.GONE else View.VISIBLE
            holder.packageName.text = item.packageName
            holder.icon.setImageDrawable(item.icon)
            holder.radioButton.isChecked = checkedItemIndex == position

            holder.itemView.setOnClickListener {
                if (checkedItemIndex == position) return@setOnClickListener

                checkedItem = item.packageName
                val tmpIndex = checkedItemIndex
                checkedItemIndex = position

                if (tmpIndex != -1) notifyItemChanged(tmpIndex)
                notifyItemChanged(checkedItemIndex)

                itemSelectListener(item.packageName)
            }
        }

        override fun submitList(list: List<AppInfo>?) {
            updateCheckedItemIndex(list)
            super.submitList(list)
        }

        companion object {
            private val itemCallback = object : DiffUtil.ItemCallback<AppInfo>() {
                override fun areItemsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                    oldInfo.packageName == newInfo.packageName

                override fun areContentsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                    oldInfo == newInfo
            }
        }
    }

    private class AppListViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val icon: ImageView = itemView.findViewById(ResourceProxy.Id.icon)
        val label: TextView = itemView.findViewById(ResourceProxy.Id.label)
        val packageName: TextView = itemView.findViewById(ResourceProxy.Id.package_name)
        val radioButton: RadioButton = itemView.findViewById(ResourceProxy.Id.radio_button)
    }

    private data class AppInfo(
        val packageName: String?,
        val label: String,
        val icon: Drawable,
    )
}
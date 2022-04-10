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

package com.android.customization;

import com.android.wallpaper.R;

/**
 * Hack of the century. For some reason still unknown,
 * aapt compiled resources are unresolved in kt files.
 * So added proxy variables to work around this mess.
 */
public class ResourceProxy {
    public static final class Id {
        public static final int themed_icon_pack_summary = R.id.themed_icon_pack_summary;

        public static final int apps_list = R.id.apps_list;
        public static final int loading_progress = R.id.loading_progress;

        public static final int icon = R.id.icon;
        public static final int label = R.id.label;
        public static final int package_name = R.id.package_name;
        public static final int radio_button = R.id.radio_button;
    }

    public static final class String {
        public static final int themed_icon_title = R.string.themed_icon_title;
        public static final int system_icons = R.string.system_icons;
    }

    public static final class Layout {
        public static final int themed_icon_pack_section_view = R.layout.themed_icon_pack_section_view;
        public static final int themed_icons_app_list_layout = R.layout.themed_icons_app_list_layout;
        public static final int themed_icons_app_list_item = R.layout.themed_icons_app_list_item;
    }
}
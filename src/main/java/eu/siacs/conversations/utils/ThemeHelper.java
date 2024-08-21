/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;

public class ThemeHelper {
	public static int find(final Context context) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		final Resources resources = context.getResources();
		final boolean dark = isDark(sharedPreferences, resources);
		final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
		switch (fontSize) {
			case "medium":
				return dark ? R.style.ConversationsTheme_Dark_Medium : R.style.ConversationsTheme_Medium;
			case "large":
				return dark ? R.style.ConversationsTheme_Dark_Large : R.style.ConversationsTheme_Large;
			default:
				return dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
		}
	}

	@Nullable
	public static Integer findThemeOverrideStyle(final Context context) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		int currentColorOverride = sharedPreferences.getInt(SettingsActivity.THEME_OVERRIDE_COLOR, -1);

		String hex = "#" + Integer.toHexString(currentColorOverride).substring(2);
		String[] colorsArray = context.getResources().getStringArray(R.array.themeColorsOverride);
		int index = -1;

		for (int i = 0;i<colorsArray.length;i++) {
			if (colorsArray[i].equals(hex)) {
				index = i + 1;
				break;
			}
		}

		switch (index) {
			case 1: return R.style.OverlayPrimary1;
			case 2: return R.style.OverlayPrimary2;
			case 3: return R.style.OverlayPrimary3;
			case 4: return R.style.OverlayPrimary4;
			case 5: return R.style.OverlayPrimary5;
			case 6: return R.style.OverlayPrimary6;
			case 7: return R.style.OverlayPrimary7;
			case 8: return R.style.OverlayPrimary8;
			case 9: return R.style.OverlayPrimary9;
			case 10: return R.style.OverlayPrimary10;
			case 11: return R.style.OverlayPrimary11;
			case 12: return R.style.OverlayPrimary12;
			case 13: return R.style.OverlayPrimary13;
			case 14: return R.style.OverlayPrimary14;
			case 15: return R.style.OverlayPrimary15;
			case 16: return R.style.OverlayPrimary16;
			case 17: return R.style.OverlayPrimary17;
			case 18: return R.style.OverlayPrimary18;
			default:
				return null;
		}
	}

	@Nullable
	@ColorInt
	public static Integer getOverriddenPrimaryColor(final Context context) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		int currentColorOverride = sharedPreferences.getInt(SettingsActivity.THEME_OVERRIDE_COLOR, -1);

		return currentColorOverride == -1 ? null : currentColorOverride;
	}

	public static int findDialog(Context context) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		final Resources resources = context.getResources();
		final boolean dark = isDark(sharedPreferences, resources);
		final String fontSize = sharedPreferences.getString("font_size", resources.getString(R.string.default_font_size));
		switch (fontSize) {
			case "medium":
				return dark ? R.style.ConversationsTheme_Dark_Dialog_Medium : R.style.ConversationsTheme_Dialog_Medium;
			case "large":
				return dark ? R.style.ConversationsTheme_Dark_Dialog_Large : R.style.ConversationsTheme_Dialog_Large;
			default:
				return dark ? R.style.ConversationsTheme_Dark_Dialog : R.style.ConversationsTheme_Dialog;
		}
	}

	private static boolean isDark(final SharedPreferences sharedPreferences, final Resources resources) {
		final String setting = sharedPreferences.getString(SettingsActivity.THEME, resources.getString(R.string.theme));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "automatic".equals(setting)) {
			return (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		} else {
			return "dark".equals(setting);
		}
	}

	public static boolean isDark(@StyleRes int id) {
		switch (id) {
			case R.style.ConversationsTheme_Dark:
			case R.style.ConversationsTheme_Dark_Large:
			case R.style.ConversationsTheme_Dark_Medium:
				return true;
			default:
				return false;
		}
	}



	public static void fix(Snackbar snackbar) {
		final Context context = snackbar.getContext();
		TypedArray typedArray = context.obtainStyledAttributes(new int[]{R.attr.TextSizeBody1});
		final float size = typedArray.getDimension(0,0f);
		typedArray.recycle();
		if (size != 0f) {
			final TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
			final TextView action = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
			if (text != null && action != null) {
				text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				action.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				action.setTextColor(ContextCompat.getColor(context, R.color.blue_a100));
			}
		}
	}

	public static boolean showColoredUsernameBackGround(Context context, boolean dark) {
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		final Resources resources = context.getResources();
		final String themeColor = sharedPreferences.getString("theme_color", resources.getString(R.string.theme_color));
		switch (themeColor) {
			case "blue":
				return dark ? false : false;
			case "blabber":
				return dark ? true : false;
			case "orange":
				return dark ? true : false;
			case "grey":
				return dark ? false : false;
			case "pink":
				return dark ? true : false;
			default:
				return dark ? true : false;
		}
	}
}

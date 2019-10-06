package com.rayworks.droidcast

import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.core.text.toSpannable
import androidx.core.text.underline


class TextUtils {
    companion object {
        fun format(boldStr: String, underline: String): Spannable =
                SpannableStringBuilder().bold { append(boldStr) }.underline { append(underline) }.toSpannable()
    }
}
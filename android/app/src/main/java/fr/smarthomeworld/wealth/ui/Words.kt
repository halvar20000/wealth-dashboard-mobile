package fr.smarthomeworld.wealth.ui

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** A plural from the resources, the count filled in as the first
 *  argument unless others are given. */
@Composable
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(
        id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))

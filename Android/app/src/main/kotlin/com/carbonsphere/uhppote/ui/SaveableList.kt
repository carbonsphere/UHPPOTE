package com.carbonsphere.uhppote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** An editable list that survives rotation; reset when any of inputs changes. */
@Composable
fun <T : Any> rememberSaveableList(vararg inputs: Any?, init: () -> List<T>): SnapshotStateList<T> =
    rememberSaveable(
        *inputs,
        saver = listSaver<SnapshotStateList<T>, T>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { init().toMutableStateList() }

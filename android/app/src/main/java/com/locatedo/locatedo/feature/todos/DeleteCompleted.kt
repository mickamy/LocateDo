package com.locatedo.locatedo.feature.todos

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.locatedo.locatedo.R

// Deletes what was already checked off, after a confirmation and without Undo. Deleting syncs, so a shared household
// loses them too, which the confirmation says.
@Composable
fun DeleteCompletedButton(count: Int, isShared: Boolean, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var isConfirming by remember { mutableStateOf(false) }
    TextButton(onClick = { isConfirming = true }, modifier = modifier) {
        Text(stringResource(R.string.todo_delete_completed), color = MaterialTheme.colorScheme.error)
    }
    if (isConfirming) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            title = { Text(pluralStringResource(R.plurals.todo_delete_completed_confirm, count, count)) },
            text = if (isShared) {
                { Text(stringResource(R.string.todo_delete_completed_shared_message)) }
            } else {
                null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirming = false
                        onConfirm()
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirming = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

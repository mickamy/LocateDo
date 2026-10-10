package com.locatedo.locatedo.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Todo
import java.util.UUID

// A choice in the assignee menu: "Anyone" when userId is null.
data class AssigneeChoice(val userId: UUID?, val name: String)

@Composable
fun Membership.shownName(): String = displayName.ifEmpty { stringResource(R.string.sharing_unnamed_member) }

// Only a household with company offers assignees; alone, every to-do is for whoever arrives.
@Composable
fun assigneeChoices(members: List<Membership>): List<AssigneeChoice> {
    if (members.size <= 1) {
        return emptyList()
    }
    return listOf(AssigneeChoice(null, stringResource(R.string.todo_assignee_anyone))) +
        members.map { AssigneeChoice(it.userId, it.shownName()) }
}

// The line under a to-do: who checked it off (only when shared), or else who it is for.
@Composable
fun todoDetail(members: List<Membership>, todo: Todo): String? {
    if (todo.isCompleted && members.size > 1) {
        val completer = members.firstOrNull { it.userId == todo.completerId }
        if (completer != null) {
            return stringResource(R.string.todo_completed_by, completer.shownName())
        }
    }
    return members.firstOrNull { it.userId == todo.assigneeId }?.shownName()
}

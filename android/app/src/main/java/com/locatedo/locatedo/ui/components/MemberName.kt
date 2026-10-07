package com.locatedo.locatedo.ui.components

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

@Composable
fun assigneeName(members: List<Membership>, userId: UUID?): String? =
    members.firstOrNull { it.userId == userId }?.shownName()

// Only a shared household says who checked a to-do off; someone no longer in it goes unnamed.
fun completer(members: List<Membership>, todo: Todo): Membership? {
    if (!todo.isCompleted || members.size <= 1) {
        return null
    }
    return members.firstOrNull { it.userId == todo.completerId }
}

// The line under a to-do: who checked it off, or else who it is for.
@Composable
fun todoDetail(members: List<Membership>, todo: Todo): String? {
    val completer = completer(members, todo)
    if (completer != null) {
        return stringResource(R.string.todo_completed_by, completer.shownName())
    }
    return assigneeName(members, todo.assigneeId)
}

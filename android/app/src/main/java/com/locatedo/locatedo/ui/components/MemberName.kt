package com.locatedo.locatedo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Membership
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

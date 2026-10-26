package com.sinobash.famly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sinobash.famly.ui.home.HomeScreen
import com.sinobash.famly.ui.person.PersonDetailScreen
import com.sinobash.famly.ui.person.PersonEditScreen
import com.sinobash.famly.ui.tree.TreeScreen

object Routes {
    const val HOME = "home"
    const val TREE = "tree"
    const val DETAIL = "detail/{personId}"
    const val EDIT = "edit?personId={personId}&linkTo={linkTo}&role={role}"

    fun detail(id: Long) = "detail/$id"
    fun edit(personId: Long = -1L, linkTo: Long = -1L, role: String = "") =
        "edit?personId=$personId&linkTo=$linkTo&role=$role"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenPerson = { nav.navigate(Routes.detail(it)) },
                onAddPerson = { nav.navigate(Routes.edit()) },
                onOpenTree = { nav.navigate(Routes.TREE) }
            )
        }
        composable(Routes.TREE) {
            TreeScreen(
                onBack = { nav.popBackStack() },
                onOpenPerson = { nav.navigate(Routes.detail(it)) }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) {
            PersonDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(personId = it)) },
                onAddRelative = { personId, role -> nav.navigate(Routes.edit(linkTo = personId, role = role)) },
                onOpenPerson = { nav.navigate(Routes.detail(it)) }
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("personId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("linkTo") { type = NavType.LongType; defaultValue = -1L },
                navArgument("role") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PersonEditScreen(onDone = { nav.popBackStack() })
        }
    }
}
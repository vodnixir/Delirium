package dev.vodnixir.delirium.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.vodnixir.delirium.ui.camera.CameraScreen
import dev.vodnixir.delirium.ui.camera.CameraViewModel
import dev.vodnixir.delirium.ui.draw.DrawScreen
import dev.vodnixir.delirium.ui.draw.DrawViewModel
import dev.vodnixir.delirium.ui.feed.FeedScreen
import dev.vodnixir.delirium.ui.feed.FeedViewModel
import dev.vodnixir.delirium.ui.friends.AddFriendScreen
import dev.vodnixir.delirium.ui.friends.AddFriendViewModel
import dev.vodnixir.delirium.ui.friends.FriendsScreen
import dev.vodnixir.delirium.ui.friends.FriendsViewModel
import dev.vodnixir.delirium.ui.splash.SplashScreen
import dev.vodnixir.delirium.ui.splash.SplashTarget
import dev.vodnixir.delirium.ui.splash.SplashViewModel
import dev.vodnixir.delirium.ui.util.appViewModel

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = SplashRoute,
    ) {
        composable<SplashRoute> {
            val vm: SplashViewModel = appViewModel { container ->
                SplashViewModel(container.authRepository, container.fcmTokenSyncer)
            }
            val target by vm.target.collectAsStateWithLifecycle()
            LaunchedEffect(target) {
                when (target) {
                    SplashTarget.Pending -> Unit
                    SplashTarget.Ready -> navController.replaceWith(FriendsRoute)
                }
            }
            SplashScreen()
        }

        composable<FriendsRoute> {
            val vm: FriendsViewModel = appViewModel { container ->
                FriendsViewModel(container.connectionRepository)
            }
            FriendsScreen(
                viewModel = vm,
                onAddFriend = { navController.navigate(AddFriendRoute) },
                onOpenFriend = { connectionId, name ->
                    navController.navigate(FeedRoute(connectionId, name))
                },
            )
        }

        composable<AddFriendRoute> {
            val vm: AddFriendViewModel = appViewModel { container ->
                AddFriendViewModel(
                    container.authRepository,
                    container.connectionRepository,
                    container.preferencesRepository,
                )
            }
            AddFriendScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<FeedRoute> { entry ->
            val route = entry.toRoute<FeedRoute>()
            val vm: FeedViewModel = appViewModel { container ->
                FeedViewModel(
                    route.connectionId,
                    container.authRepository,
                    container.photoRepository,
                    container.appContext,
                )
            }
            FeedScreen(
                viewModel = vm,
                friendName = route.friendName,
                onTakePhoto = { navController.navigate(CameraRoute(route.connectionId)) },
                onDraw = { navController.navigate(DrawRoute(route.connectionId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<CameraRoute> { entry ->
            val route = entry.toRoute<CameraRoute>()
            val vm: CameraViewModel = appViewModel { container ->
                CameraViewModel(
                    route.connectionId,
                    container.authRepository,
                    container.photoRepository,
                )
            }
            CameraScreen(
                viewModel = vm,
                onClose = { navController.popBackStack() },
                onPhotoSent = { navController.popBackStack() },
            )
        }

        composable<DrawRoute> { entry ->
            val route = entry.toRoute<DrawRoute>()
            val vm: DrawViewModel = appViewModel { container ->
                DrawViewModel(
                    route.connectionId,
                    container.authRepository,
                    container.photoRepository,
                )
            }
            DrawScreen(
                viewModel = vm,
                backgroundUri = route.backgroundUri,
                onClose = { navController.popBackStack() },
                onSent = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.replaceWith(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

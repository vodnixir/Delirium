package dev.vodnixir.delirium.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.first
import dev.vodnixir.delirium.ui.auth.AuthViewModel
import dev.vodnixir.delirium.ui.auth.LoginScreen
import dev.vodnixir.delirium.ui.auth.RegisterScreen
import dev.vodnixir.delirium.ui.camera.CameraScreen
import dev.vodnixir.delirium.ui.camera.CameraViewModel
import dev.vodnixir.delirium.ui.draw.DrawScreen
import dev.vodnixir.delirium.ui.draw.DrawViewModel
import dev.vodnixir.delirium.ui.feed.FeedScreen
import dev.vodnixir.delirium.ui.feed.FeedViewModel
import dev.vodnixir.delirium.ui.friends.AddFriendScreen
import dev.vodnixir.delirium.ui.friends.AddFriendViewModel
import dev.vodnixir.delirium.ui.main.CaptureViewModel
import dev.vodnixir.delirium.ui.main.MainScreen
import dev.vodnixir.delirium.ui.main.MainViewModel
import dev.vodnixir.delirium.ui.photo.PhotoDetailScreen
import dev.vodnixir.delirium.ui.photo.PhotoDetailViewModel
import dev.vodnixir.delirium.ui.profile.ProfileScreen
import dev.vodnixir.delirium.ui.profile.ProfileViewModel
import dev.vodnixir.delirium.ui.recap.RecapScreen
import dev.vodnixir.delirium.ui.recap.RecapViewModel
import dev.vodnixir.delirium.ui.settings.SettingsScreen
import dev.vodnixir.delirium.ui.settings.SettingsViewModel
import dev.vodnixir.delirium.ui.settings.WidgetSetupScreen
import dev.vodnixir.delirium.ui.splash.SplashScreen
import dev.vodnixir.delirium.ui.splash.SplashTarget
import dev.vodnixir.delirium.ui.splash.SplashViewModel
import dev.vodnixir.delirium.ui.util.appViewModel

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController(),
    deepLinkConnectionId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    // A widget tap launches us with a connectionId. Wait until the user is past the
    // auth/splash screens (so they're signed in) before opening that friend's feed.
    LaunchedEffect(deepLinkConnectionId) {
        val connectionId = deepLinkConnectionId ?: return@LaunchedEffect
        navController.currentBackStackEntryFlow.first { entry ->
            val dest = entry.destination
            !dest.hasRoute<SplashRoute>() &&
                !dest.hasRoute<LoginRoute>() &&
                !dest.hasRoute<RegisterRoute>()
        }
        navController.navigate(FeedRoute(connectionId, "")) { launchSingleTop = true }
        onDeepLinkConsumed()
    }
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
                    SplashTarget.Login -> navController.replaceWith(LoginRoute)
                    SplashTarget.Main -> navController.replaceWith(MainRoute)
                }
            }
            SplashScreen()
        }

        composable<LoginRoute> {
            val vm: AuthViewModel = appViewModel { container ->
                AuthViewModel(
                    container.authRepository,
                    container.preferencesRepository,
                    container.fcmTokenSyncer,
                    container.appContext,
                )
            }
            LoginScreen(
                viewModel = vm,
                onAuthenticated = { navController.replaceWith(MainRoute) },
                onGoToRegister = { navController.navigate(RegisterRoute) },
            )
        }

        composable<RegisterRoute> {
            val vm: AuthViewModel = appViewModel { container ->
                AuthViewModel(
                    container.authRepository,
                    container.preferencesRepository,
                    container.fcmTokenSyncer,
                    container.appContext,
                )
            }
            RegisterScreen(
                viewModel = vm,
                onAuthenticated = { navController.replaceWith(MainRoute) },
                onGoToLogin = { navController.popBackStack() },
            )
        }

        composable<MainRoute> {
            val vm: MainViewModel = appViewModel { container ->
                MainViewModel(container.connectionRepository, container.photoRepository)
            }
            val captureVm: CaptureViewModel = appViewModel { container ->
                CaptureViewModel(
                    container.authRepository,
                    container.outboxRepository,
                    container.appContext,
                )
            }
            MainScreen(
                viewModel = vm,
                captureViewModel = captureVm,
                onAddFriend = { navController.navigate(AddFriendRoute) },
                onOpenFriend = { connectionId, name ->
                    navController.navigate(FeedRoute(connectionId, name))
                },
                onOpenPhoto = { photoId, connectionId ->
                    navController.navigate(PhotoDetailRoute(photoId, connectionId))
                },
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenRecap = { navController.navigate(RecapRoute) },
            )
        }

        composable<RecapRoute> {
            val vm: RecapViewModel = appViewModel { container ->
                RecapViewModel(container.connectionRepository, container.photoRepository)
            }
            RecapScreen(
                viewModel = vm,
                onClose = { navController.popBackStack() },
            )
        }

        composable<ProfileRoute> {
            val vm: ProfileViewModel = appViewModel { container ->
                ProfileViewModel(
                    container.authRepository,
                    container.connectionRepository,
                    container.photoRepository,
                    container.preferencesRepository,
                    container.appContext,
                )
            }
            ProfileScreen(
                viewModel = vm,
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenWidgetSetup = { navController.navigate(WidgetSetupRoute) },
                onLoggedOut = { navController.replaceWith(LoginRoute) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            val vm: SettingsViewModel = appViewModel { container ->
                SettingsViewModel(container.preferencesRepository, container.appContext)
            }
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable<WidgetSetupRoute> {
            WidgetSetupScreen(onBack = { navController.popBackStack() })
        }

        composable<AddFriendRoute> {
            val vm: AddFriendViewModel = appViewModel { container ->
                AddFriendViewModel(
                    container.connectionRepository,
                    container.preferencesRepository,
                    container.appContext,
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
                    route.friendName,
                    container.authRepository,
                    container.photoRepository,
                    container.connectionRepository,
                    container.outboxRepository,
                    container.appContext,
                )
            }
            FeedScreen(
                viewModel = vm,
                onTakePhoto = { navController.navigate(CameraRoute(route.connectionId)) },
                onDraw = { navController.navigate(DrawRoute(route.connectionId)) },
                onOpenPhoto = { photoId ->
                    navController.navigate(PhotoDetailRoute(photoId, route.connectionId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<PhotoDetailRoute> { entry ->
            val route = entry.toRoute<PhotoDetailRoute>()
            val vm: PhotoDetailViewModel = appViewModel { container ->
                PhotoDetailViewModel(
                    route.photoId,
                    route.connectionId,
                    container.authRepository,
                    container.photoRepository,
                    container.connectionRepository,
                    container.reactionsRepository,
                    container.chatRepository,
                )
            }
            PhotoDetailScreen(
                viewModel = vm,
                onClose = { navController.popBackStack() },
            )
        }

        composable<CameraRoute> { entry ->
            val route = entry.toRoute<CameraRoute>()
            val vm: CameraViewModel = appViewModel { container ->
                CameraViewModel(
                    route.connectionId,
                    container.authRepository,
                    container.outboxRepository,
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
                    container.outboxRepository,
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

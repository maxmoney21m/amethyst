package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            TabRow(
                backgroundColor = MaterialTheme.colors.background,
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                        color = MaterialTheme.colors.primary
                    )
                },
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = {
                        Text(text = stringResource(R.string.new_threads))
                    }
                )

                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = {
                        Text(text = stringResource(R.string.conversations))
                    }
                )

                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = {
                        Text(text = "Global")
                    }
                )
            }
            HorizontalPager(count = 3, state = pagerState) {
                when (pagerState.currentPage) {
                    0 -> FollowingFeedView(accountViewModel, navController)
                    1 -> RepliesFeedView(accountViewModel, navController)
                    2 -> GlobalFeedView(accountViewModel, navController)
                }
            }
        }
    }
}

@Composable
fun FollowingFeedView(accountViewModel: AccountViewModel, navController: NavController) =
    FeedViewWrapper<NostrHomeFeedViewModel>(
        accountViewModel,
        navController,
        HomeNewThreadFeedFilter,
        NostrHomeDataSource,
        Route.Home.route + "Follows"
    )

@Composable
fun RepliesFeedView(accountViewModel: AccountViewModel, navController: NavController) =
    FeedViewWrapper<NostrHomeRepliesFeedViewModel>(
        accountViewModel,
        navController,
        HomeConversationsFeedFilter,
        NostrHomeDataSource,
        Route.Home.route + "FollowsReplies"
    )


@Composable
fun GlobalFeedView(accountViewModel: AccountViewModel, navController: NavController) =
    FeedViewWrapper<NostrGlobalFeedViewModel>(
        accountViewModel,
        navController,
        GlobalFeedFilter,
        NostrGlobalDataSource,
        Route.Home.route + "Global"
    )

@Composable
inline fun <reified T : FeedViewModel> FeedViewWrapper(
    accountViewModel: AccountViewModel,
    navController: NavController,
    feedFilter: FeedFilter<Note>,
    dataSource: NostrDataSource,
    routeForLastRead: String?
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    feedFilter.account = account

    val feedViewModel: T = viewModel()

    LaunchedEffect(accountViewModel) {
        NostrHomeDataSource.resetFilters()
        feedViewModel.refresh()
    }

    val lifeCycleOwner = LocalLifecycleOwner.current

    RegisterFeedObserver(feedViewModel, accountViewModel, dataSource, lifeCycleOwner)
    FeedView(
        feedViewModel,
        accountViewModel,
        navController,
        routeForLastRead
    ) //Route.Home.route + "Follows")
}

@Composable
fun RegisterFeedObserver(
    feedViewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    dataSource: NostrDataSource,
    lifeCycleOwner: LifecycleOwner
) {
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dataSource.start()
                feedViewModel.refresh()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                dataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
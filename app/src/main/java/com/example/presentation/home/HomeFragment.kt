package com.example.presentation.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.presentation.MainActivity
import com.example.presentation.compose.HomeScreenCompose
import com.example.presentation.compose.WifiCardTheme
import com.example.service.TestService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/**
 * Modern Jetpack Compose Home Screen
 * Connects 100% to HomeViewModel, TestService foreground execution, and navigation.
 */
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    HomeScreenCompose(
                        viewModel = viewModel,
                        onDrawerClick = {
                            (activity as? MainActivity)?.openDrawer()
                        },
                        onNavigateToRouters = {
                            try {
                                findNavController().navigate(R.id.nav_router_manager_fragment)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed navigating to router manager safely")
                            }
                        },
                        onNavigateToTest = {
                            try {
                                findNavController().navigate(R.id.action_home_fragment_to_test_fragment)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed navigating to test fragment safely")
                            }
                        },
                        onStopTest = {
                            val serviceIntent = Intent(requireContext(), TestService::class.java).apply {
                                action = TestService.ACTION_CANCEL
                            }
                            requireContext().startService(serviceIntent)
                            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.toast_test_cancelled))
                        },
                        showTopHeader = false
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.errorFlow.collectLatest { err ->
                        val ctx = context ?: return@collectLatest
                        com.example.util.ToastHelper.showErrorToast(ctx, err)
                    }
                }

                launch {
                    viewModel.startTestEvent.collect { config ->
                        val ctx = context ?: return@collect
                        val serviceIntent = Intent(ctx, TestService::class.java).apply {
                            action = TestService.ACTION_START
                            putExtra(TestService.EXTRA_ROUTER_ID, config.routerId)
                            putStringArrayListExtra(TestService.EXTRA_CARD_LIST, config.cardList)
                            putExtra(TestService.EXTRA_DELAY_MS, config.delayMs)
                        }
                        try {
                            ContextCompat.startForegroundService(ctx, serviceIntent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to start TestService in foreground")
                            com.example.util.ToastHelper.showErrorToast(ctx, getString(R.string.toast_service_start_failed, e.localizedMessage ?: ""))
                        }

                        // Navigate to TestFragment using bundle for compile safety
                        val bundle = Bundle().apply {
                            putLong("routerId", config.routerId)
                            putStringArray("cardList", config.cardList.toTypedArray())
                            putLong("delayMs", config.delayMs)
                        }
                        if (isAdded) {
                            try {
                                findNavController().navigate(R.id.action_home_fragment_to_test_fragment, bundle)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to navigate to test fragment safely")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startMonitoringConnection(requireContext())
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopMonitoringConnection()
    }
}

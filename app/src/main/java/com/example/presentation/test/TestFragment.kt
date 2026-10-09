package com.example.presentation.test

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.presentation.compose.TestScreenCompose
import com.example.presentation.compose.WifiCardTheme
import com.example.service.TestService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/**
 * Modern Jetpack Compose Test Screen
 * Connects 100% to TestViewModel and TestService foreground lifecycle.
 */
class TestFragment : Fragment() {

    private val viewModel: TestViewModel by viewModel()
    private var wasTestActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    TestScreenCompose(
                        viewModel = viewModel,
                        onPauseToggle = { isCurrentlyPaused ->
                            val serviceIntent = Intent(requireContext(), TestService::class.java).apply {
                                action = if (isCurrentlyPaused) TestService.ACTION_RESUME else TestService.ACTION_PAUSE
                            }
                            requireContext().startService(serviceIntent)
                        },
                        onCancel = {
                            com.example.util.DialogHelper.showCustomDialog(
                                context = requireContext(),
                                title = getString(R.string.dialog_cancel_test_title),
                                message = getString(R.string.dialog_cancel_test_msg),
                                dialogType = com.example.util.DialogHelper.DialogType.WARNING,
                                iconRes = R.drawable.ic_stop_modern,
                                positiveButtonText = getString(R.string.dialog_cancel_test_pos),
                                positiveAction = {
                                    val serviceIntent = Intent(requireContext(), TestService::class.java).apply {
                                        action = TestService.ACTION_CANCEL
                                    }
                                    requireContext().startService(serviceIntent)
                                    findNavController().popBackStack()
                                },
                                negativeButtonText = getString(R.string.dialog_cancel_test_neg)
                            )
                        },
                        onNavigateHome = {
                            try {
                                findNavController().navigate(R.id.nav_home_fragment)
                            } catch (e: Exception) {
                                findNavController().popBackStack(R.id.nav_home_fragment, false)
                            }
                        },
                        showTopHeader = false
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeServiceEvents()
    }

    private fun observeServiceEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.serviceState.collectLatest { state ->
                    if (state.status == "RUNNING" || state.status == "PAUSED") {
                        wasTestActive = true
                    }
                    if (state.status == "DONE" && wasTestActive) {
                        wasTestActive = false
                        val ctx = context
                        if (ctx != null && isAdded) {
                            try {
                                com.example.util.ToastHelper.showSuccessToast(ctx, getString(R.string.test_completed_toast))
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to toast test completion in TestFragment")
                            }
                        }
                    }
                }
            }
        }
    }
}

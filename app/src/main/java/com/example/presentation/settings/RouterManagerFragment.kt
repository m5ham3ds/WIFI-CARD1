package com.example.presentation.settings

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
import com.example.presentation.compose.RouterManagerScreenCompose
import com.example.presentation.compose.WifiCardTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class RouterManagerFragment : Fragment() {

    private val viewModel: RouterManagerViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    RouterManagerScreenCompose(
                        viewModel = viewModel,
                        onAddRouter = {
                            val bundle = Bundle().apply {
                                putLong("routerId", -1L)
                            }
                            findNavController().navigate(R.id.nav_router_form_fragment, bundle)
                        },
                        onEditRouter = { id ->
                            val bundle = Bundle().apply {
                                putLong("routerId", id)
                            }
                            findNavController().navigate(R.id.nav_router_form_fragment, bundle)
                        },
                        onBack = {
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        },
                        showTopHeader = false
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collectLatest { event ->
                    when (event) {
                        is RouterManagerViewModel.UiEvent.ShowMessage -> {
                            val ctx = context ?: return@collectLatest
                            com.example.util.ToastHelper.showSuccessToast(ctx, getString(event.resId))
                        }
                        is RouterManagerViewModel.UiEvent.NavigateToTest -> {}
                    }
                }
            }
        }
    }
}

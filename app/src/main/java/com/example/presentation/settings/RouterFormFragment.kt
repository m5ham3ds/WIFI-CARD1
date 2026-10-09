package com.example.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.presentation.compose.RouterFormScreenCompose
import com.example.presentation.compose.WifiCardTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class RouterFormFragment : Fragment() {

    private val viewModel: RouterManagerViewModel by viewModel()
    private var targetRouterId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetRouterId = arguments?.getLong("routerId", -1L) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val routers by viewModel.routers.collectAsState()
                val existing = if (targetRouterId != -1L) routers.firstOrNull { it.id == targetRouterId } else null

                WifiCardTheme {
                    RouterFormScreenCompose(
                        existingProfile = existing,
                        onSave = { entity ->
                            if (targetRouterId == -1L) {
                                viewModel.addRouter(entity)
                            } else {
                                viewModel.updateRouter(entity)
                            }
                            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.router_form_saved))
                            findNavController().popBackStack()
                        },
                        onCancel = {
                            findNavController().popBackStack()
                        },
                        showTopHeader = false
                    )
                }
            }
        }
    }
}


package com.example.presentation.history

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
import com.example.R
import com.example.presentation.compose.HistoryScreenCompose
import com.example.presentation.compose.WifiCardTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Modern Jetpack Compose History Fragment
 * Seamlessly integrates HistoryScreenCompose and SessionDetailCompose
 */
class HistoryFragment : Fragment() {

    private val viewModel: HistoryViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    HistoryScreenCompose(
                        viewModel = viewModel,
                        onBack = {
                            if (viewModel.selectedSessionId.value != null) {
                                viewModel.clearSessionSelection()
                            } else {
                                activity?.onBackPressedDispatcher?.onBackPressed()
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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.selectedSessionId.value != null) {
                    viewModel.clearSessionSelection()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        observeExportStatus()
        observeSelectedSession()
    }

    private fun observeSelectedSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedSessionId.collectLatest { sessionId ->
                    val act = activity ?: return@collectLatest
                    val tvTitle = act.findViewById<android.widget.TextView?>(R.id.tv_header_title)
                    val tvSubtitle = act.findViewById<android.widget.TextView?>(R.id.tv_header_subtitle)
                    val btnNav = act.findViewById<android.widget.ImageButton?>(R.id.btn_pages_nav)
                    if (sessionId != null) {
                        val session = viewModel.sessions.value.firstOrNull { it.id == sessionId }
                        tvTitle?.text = getString(R.string.history_detail_title, sessionId)
                        tvSubtitle?.text = if (session != null) getString(R.string.history_detail_router_sub, session.routerName) else getString(R.string.history_detail_default_sub)
                        btnNav?.setImageResource(R.drawable.ic_back)
                        btnNav?.setOnClickListener {
                            viewModel.clearSessionSelection()
                        }
                    } else {
                        tvTitle?.text = getString(R.string.menu_history)
                        tvSubtitle?.text = getString(R.string.header_history_sub)
                        btnNav?.setImageResource(R.drawable.ic_menu)
                        btnNav?.setOnClickListener {
                            act.findViewById<androidx.drawerlayout.widget.DrawerLayout?>(R.id.drawer_layout)?.openDrawer(androidx.core.view.GravityCompat.START)
                        }
                    }
                }
            }
        }
    }

    private fun observeExportStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportEvent.collectLatest { event ->
                    val ctx = context ?: return@collectLatest
                    when (event) {
                        is HistoryViewModel.ExportEvent.Success -> {
                            com.example.util.ToastHelper.showSuccessToast(ctx, event.message)
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.fileprovider",
                                    event.file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, event.file.name)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.export_share_title)))
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to share export via sharesheet")
                            }
                        }
                        is HistoryViewModel.ExportEvent.Error -> {
                            com.example.util.ToastHelper.showErrorToast(ctx, event.message)
                        }
                    }
                }
            }
        }
    }
}

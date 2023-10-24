package ru.netology.nmedia.activity

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.netology.nmedia.R
import ru.netology.nmedia.adapter.PostLoadingStateAdapter
import ru.netology.nmedia.adapters.OnInteractionListener
import ru.netology.nmedia.adapters.PostAdapter
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.auxiliary.Companion.Companion.longArg
import ru.netology.nmedia.auxiliary.Companion.Companion.textArg
import ru.netology.nmedia.auxiliary.FloatingValue.currentFragment
import ru.netology.nmedia.databinding.FragmentFeedBinding
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.viewmodel.AuthViewModel
import ru.netology.nmedia.viewmodel.PostViewModel
import javax.inject.Inject


@AndroidEntryPoint
class FeedFragment : Fragment() {

    val viewModel: PostViewModel by activityViewModels()

    val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var appAuth: AppAuth


    private val interactionListener = object : OnInteractionListener {

        override fun onLike(post: Post) {
            if (authViewModel.authenticated) {
                viewModel.likeById(post)
            } else {
                AlertDialog.Builder(context)
                    .setMessage(R.string.action_not_allowed)
                    .setPositiveButton(R.string.sign_up) { _, _ ->
                        findNavController().navigate(
                            R.id.action_feedFragment_to_authFragment,
                            Bundle().apply {
                                textArg = getString(R.string.sign_up)
                            }
                        )
                    }
                    .setNeutralButton(R.string.sign_in) { _, _ ->
                        findNavController().navigate(
                            R.id.action_feedFragment_to_authFragment,
                            Bundle().apply {
                                textArg = getString(R.string.sign_in)
                            }
                        )
                    }
                    .setNegativeButton(R.string.no, null)
                    .setCancelable(true)
                    .create()
                    .show()
            }
        }

        override fun onShare(post: Post) {
            //viewModel.shareById(post.id)
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, post.content)
            }

            val shareIntent =
                Intent.createChooser(intent, getString(R.string.chooser_share_post))
            startActivity(shareIntent)
        }

        override fun onRemove(post: Post) {
            viewModel.removeById(post.id)
        }

        override fun onEdit(post: Post) {
            viewModel.edit(post)
            findNavController().navigate(
                R.id.action_feedFragment_to_newPostFragment,
                Bundle().apply {
                    textArg = post.content
                })

        }

        override fun onPlayVideo(post: Post) {
            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(post.attachment?.url))
            if (playIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(playIntent)
            }
        }

        override fun onPreviewPost(post: Post) {
            findNavController().navigate(
                R.id.action_feedFragment_to_postFragment,
                Bundle().apply {
                    longArg = post.id
                })
        }

        override fun onPreviewAttachment(post: Post) {
            findNavController().navigate(
                R.id.action_feedFragment_to_viewImageAttach,
                Bundle().apply {
                    textArg = post.attachment?.url
                })
        }
    }

    private lateinit var binding: FragmentFeedBinding
    private lateinit var adapter: PostAdapter

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentFeedBinding.inflate(layoutInflater)
        adapter = PostAdapter(interactionListener)

        binding.list.adapter = adapter.withLoadStateHeaderAndFooter(
            header = PostLoadingStateAdapter(object : PostLoadingStateAdapter.OnInteractionListener {
                override fun onRetry() {
                    adapter.retry()
                }
            }),
            footer = PostLoadingStateAdapter(object : PostLoadingStateAdapter.OnInteractionListener {
                override fun onRetry() {
                    adapter.refresh()

                }
            }),
        )

        lifecycleScope.launchWhenCreated {
            viewModel.data.collectLatest {
                adapter.submitData(it)
            }
        }


        lifecycleScope.launchWhenCreated {
            adapter.loadStateFlow.collectLatest {
                it.refresh is LoadState.Loading
            }
        }

        authViewModel.data.observe(viewLifecycleOwner) {
            viewModel.refreshPosts(adapter)
        }

        binding.swipe.setOnRefreshListener {
            viewModel.refreshPosts(adapter)
        }

        viewModel.dataState.observe(viewLifecycleOwner) {
            binding.progress.isVisible = it is FeedModelState.Loading
            binding.swipe.isRefreshing = it is FeedModelState.Refresh
            if (it is FeedModelState.Error) {
                Snackbar.make(binding.root, R.string.error_loading, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry_loading) { viewModel.loadPosts() }
                    .show()
            }
            if (it is FeedModelState.Idle) {
                Toast.makeText(context, R.string.on_success, Toast.LENGTH_SHORT).show()
            }
        }


        var menuProvider: MenuProvider? = null

        authViewModel.data.observe(viewLifecycleOwner) {
            menuProvider?.let(requireActivity()::removeMenuProvider)
            requireActivity().addMenuProvider(object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_main, menu)

                    menu.setGroupVisible(R.id.unauthenticated, !authViewModel.authenticated)
                    menu.setGroupVisible(R.id.authenticated, authViewModel.authenticated)

                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.signin -> {
                            findNavController().navigate(
                                R.id.action_feedFragment_to_authFragment,
                                Bundle().apply {
                                    textArg = getString(R.string.sign_in)
                                }
                            )
                            true
                        }
                        R.id.signup -> {
                            findNavController().navigate(
                                R.id.action_feedFragment_to_authFragment,
                                Bundle().apply {
                                    textArg = getString(R.string.sign_up)
                                }
                            )
                            true
                        }
                        R.id.signout -> {
                            AlertDialog.Builder(requireActivity())
                                .setTitle(R.string.are_you_suare)
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    appAuth.removeAuth()
                                }
                                .setCancelable(true)
                                .setNegativeButton(R.string.no, null)
                                .create()
                                .show()
                            true
                        }
                        else -> false
                    }
                }
            }.apply {
                menuProvider = this
            }, viewLifecycleOwner)
        }
        binding.fab.setOnClickListener {
            if (authViewModel.authenticated) {
                findNavController().navigate(R.id.action_feedFragment_to_newPostFragment)
            } else {
                AlertDialog.Builder(context)
                    .setMessage(R.string.action_not_allowed)
                    .setPositiveButton(R.string.sign_up) { _, _ ->
                        findNavController().navigate(
                            R.id.action_feedFragment_to_authFragment,
                            Bundle().apply {
                                textArg = getString(R.string.sign_up)
                            }
                        )
                    }
                    .setNeutralButton(R.string.sign_in) { _, _ ->
                        findNavController().navigate(
                            R.id.action_feedFragment_to_authFragment,
                            Bundle().apply {
                                textArg = getString(R.string.sign_in)
                            }
                        )
                    }
                    .setNegativeButton(R.string.no, null)
                    .setCancelable(true)
                    .create()
                    .show()
            }
        }
        binding.retryButton.setOnClickListener {
            viewModel.loadPosts()
        }

        binding.newerCount.setOnClickListener {
            binding.newerCount.isVisible = false
            viewModel.refreshPosts(adapter)
            binding.list.smoothScrollToPosition(0)
        }

        binding.list.addOnScrollListener ( //Скрываем плашку через 2 секунды после окончания скроллинга
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    lifecycleScope.launch {
                        delay(3_000)
                        binding.newerCount.visibility = View.GONE
                    }
                }
            }
        )

        lifecycleScope.launchWhenStarted {
            viewModel.newerCount.collectLatest { state ->
                binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        binding.newerCount.isVisible =
                            layoutManager.findFirstCompletelyVisibleItemPosition() != 0 && state > 0
                    }
                })
            }
        }
        return binding.root
    }

    override fun onResume() {
        currentFragment = javaClass.simpleName
        super.onResume()
    }
}

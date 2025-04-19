package com.example.explorelens.ui.user



import android.os.Bundle

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.Toast

import androidx.fragment.app.Fragment

import androidx.lifecycle.ViewModelProvider

import androidx.navigation.fragment.findNavController

import com.bumptech.glide.Glide

import com.bumptech.glide.request.RequestOptions

import com.example.explorelens.R

import com.example.explorelens.databinding.FragmentProfileBinding



class ProfileFragment : Fragment() {



    private var _binding: FragmentProfileBinding? = null

    private val binding get() = _binding!!



    private lateinit var viewModel: ProfileViewModel



    override fun onCreateView(

        inflater: LayoutInflater,

        container: ViewGroup?,

        savedInstanceState: Bundle?

    ): View {

        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        return binding.root

    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)



        viewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)



        setupObservers()

        setupRefreshListener()



        binding.logoutButton.setOnClickListener {

            viewModel.logout()

        }



// Fetch user data when the fragment is created

        viewModel.fetchUserData()

    }



    private fun setupObservers() {

        viewModel.userState.observe(viewLifecycleOwner) { state ->

            when (state) {

                is UserState.Loading -> {

                    binding.progressBar.visibility = View.VISIBLE

                    binding.errorMessage.visibility = View.GONE

                    binding.profileContent.visibility = View.GONE

                }

                is UserState.Success -> {

                    binding.progressBar.visibility = View.GONE

                    binding.errorMessage.visibility = View.GONE

                    binding.profileContent.visibility = View.VISIBLE



// Update UI with user data

                    binding.usernameText.text = state.user.username

                    binding.emailText.text = state.user.email



// Load profile picture with Glide

                    if (!state.user.profilePictureUrl.isNullOrEmpty()) {

                        Glide.with(this)

                            .load(state.user.profilePictureUrl)

                            .apply(RequestOptions.circleCropTransform())

                            .placeholder(R.drawable.default_profile_image)

                            .error(R.drawable.default_profile_image)

                            .into(binding.profileImage)

                    } else {

                        binding.profileImage.setImageResource(R.drawable.default_profile_image)

                    }

                }

                is UserState.Error -> {

                    binding.progressBar.visibility = View.GONE

                    binding.profileContent.visibility = View.GONE

                    binding.errorMessage.visibility = View.VISIBLE

                    binding.errorMessage.text = state.message



                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()

                }

                is UserState.Logout -> {

                    findNavController().navigate(R.id.action_profileFragment_to_loginFragment)

                }

            }

        }

    }



    private fun setupRefreshListener() {

        binding.swipeRefresh.setOnRefreshListener {

            viewModel.fetchUserData()

            binding.swipeRefresh.isRefreshing = false

        }

    }



    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null

    }

}
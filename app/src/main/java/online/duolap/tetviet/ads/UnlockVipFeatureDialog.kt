package online.duolap.tetviet.ads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import online.duolap.tetviet.R
import online.duolap.tetviet.activities.VipActivity
import online.duolap.tetviet.data.local.VipPreferences
import online.duolap.tetviet.databinding.DialogUnlockVipFeatureBinding
import javax.inject.Inject

/**
 * BottomSheet hiển thị khi người dùng chọn tính năng / sticker / hình nền VIP.
 * Cung cấp 2 lựa chọn:
 * 1. Xem 1 video quảng cáo ngắn để mở khoá miễn phí trong 24h.
 * 2. Nâng cấp VIP để sở hữu vĩnh viễn không giới hạn và không quảng cáo.
 */
@AndroidEntryPoint
class UnlockVipFeatureDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var vipPreferences: VipPreferences

    private var _binding: DialogUnlockVipFeatureBinding? = null
    private val binding get() = _binding!!

    private var featureId: String = ""
    private var featureName: String = ""
    private var onUnlockedCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        featureId = arguments?.getString(ARG_FEATURE_ID) ?: ""
        featureName = arguments?.getString(ARG_FEATURE_NAME) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUnlockVipFeatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (featureName.isNotEmpty()) {
            binding.tvUnlockFeatureName.text = featureName
        }

        binding.btnWatchAdUnlock.setOnClickListener {
            handleWatchAd()
        }

        binding.btnGoVip.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), VipActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleWatchAd() {
        val currentActivity = activity ?: return

        Toast.makeText(
            currentActivity,
            getString(R.string.msg_ad_loading_short),
            Toast.LENGTH_SHORT
        ).show()

        adManager.showRewardedAd(
            activity = currentActivity,
            onUserEarnedReward = {
                vipPreferences.unlockFeatureTemporarily(featureId)
                Toast.makeText(
                    currentActivity,
                    getString(R.string.msg_feature_unlocked_success),
                    Toast.LENGTH_LONG
                ).show()
                onUnlockedCallback?.invoke()
                dismiss()
            },
            onDismissed = {
                // Đóng khi xem xong hoặc tắt quảng cáo
            },
            onError = { errorMessage ->
                Toast.makeText(currentActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun setOnUnlockedListener(callback: () -> Unit) {
        this.onUnlockedCallback = callback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FEATURE_ID = "arg_feature_id"
        private const val ARG_FEATURE_NAME = "arg_feature_name"

        fun newInstance(
            featureId: String,
            featureName: String,
            onUnlocked: () -> Unit
        ): UnlockVipFeatureDialog {
            val fragment = UnlockVipFeatureDialog()
            val bundle = Bundle().apply {
                putString(ARG_FEATURE_ID, featureId)
                putString(ARG_FEATURE_NAME, featureName)
            }
            fragment.arguments = bundle
            fragment.onUnlockedCallback = onUnlocked
            return fragment
        }
    }
}


package online.duolap.tetviet.ads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
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
 *
 * Chế độ "chỉ dành cho VIP" ([newVipOnlyInstance]) ẩn lựa chọn xem quảng cáo và có thể hiện ô xem trước,
 * vd. lời chúc viết bằng phông chữ VIP.
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
    private var isVipOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        featureId = arguments?.getString(ARG_FEATURE_ID) ?: ""
        featureName = arguments?.getString(ARG_FEATURE_NAME) ?: ""
        isVipOnly = arguments?.getBoolean(ARG_VIP_ONLY) ?: false
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

        if (isVipOnly) {
            binding.btnWatchAdUnlock.visibility = View.GONE
            arguments?.getString(ARG_TITLE)?.let { binding.tvUnlockTitle.text = it }
            val previewFontResId = arguments?.getInt(ARG_PREVIEW_FONT_RES) ?: 0
            val previewText = arguments?.getString(ARG_PREVIEW_TEXT).orEmpty()
            if (previewFontResId != 0 && previewText.isNotEmpty()) {
                binding.tvUnlockPreview.typeface = ResourcesCompat.getFont(requireContext(), previewFontResId)
                binding.tvUnlockPreview.text = previewText
                binding.layoutUnlockPreview.visibility = View.VISIBLE
            }
            // Chỉ còn 1 lựa chọn nên làm nổi nút nâng cấp bằng viền vàng
            binding.btnGoVip.setBackgroundResource(R.drawable.bg_vip_card_gold)
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
            placement = AdPlacement.UNLOCK_FEATURE,
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
        private const val ARG_VIP_ONLY = "arg_vip_only"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_PREVIEW_TEXT = "arg_preview_text"
        private const val ARG_PREVIEW_FONT_RES = "arg_preview_font_res"

        /**
         * Tính năng chỉ mở bằng cách mua VIP (không có lựa chọn xem quảng cáo).
         * @param subtitle dòng mô tả dưới tiêu đề
         * @param previewText nội dung xem trước, hiển thị bằng [previewFontResId] (0 = không xem trước)
         */
        fun newVipOnlyInstance(
            title: String,
            subtitle: String,
            previewText: String = "",
            @FontRes previewFontResId: Int = 0
        ): UnlockVipFeatureDialog {
            return UnlockVipFeatureDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_VIP_ONLY, true)
                    putString(ARG_TITLE, title)
                    putString(ARG_FEATURE_NAME, subtitle)
                    putString(ARG_PREVIEW_TEXT, previewText)
                    putInt(ARG_PREVIEW_FONT_RES, previewFontResId)
                }
            }
        }

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


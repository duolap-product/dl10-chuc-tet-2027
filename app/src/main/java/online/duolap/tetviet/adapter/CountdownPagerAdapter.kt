package online.duolap.tetviet.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import online.duolap.tetviet.R
import online.duolap.tetviet.data.model.MondayCountdownInfo
import online.duolap.tetviet.databinding.ItemCountdownMondayBinding
import online.duolap.tetviet.databinding.ItemCountdownTimerBinding

class CountdownPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_TIMER = 0
        const val TYPE_MONDAY = 1
    }

    private var currentDays: Long = 0L
    private var currentHours: Long = 0L
    private var currentMinutes: Long = 0L
    private var currentSeconds: Long = 0L
    private var isTimerFinished: Boolean = false

    private var currentMondayInfo: MondayCountdownInfo? = null

    private var timerViewHolder: TimerViewHolder? = null
    private var mondayViewHolder: MondayViewHolder? = null

    inner class TimerViewHolder(val itemBinding: ItemCountdownTimerBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        @SuppressLint("DefaultLocale")
        fun bind(days: Long, hours: Long, minutes: Long, seconds: Long, isFinished: Boolean) {
            if (!isFinished) {
                itemBinding.tvDays.text = days.toString()
                itemBinding.tvHours.text = String.format("%02d", hours)
                itemBinding.tvMinutes.text = String.format("%02d", minutes)
                itemBinding.tvSeconds.text = String.format("%02d", seconds)
                itemBinding.tvCountdownWelcome.text = itemBinding.root.context.getString(R.string.countdown_welcome)
            } else {
                itemBinding.tvDays.text = "0"
                itemBinding.tvHours.text = "00"
                itemBinding.tvMinutes.text = "00"
                itemBinding.tvSeconds.text = "00"
                itemBinding.tvCountdownWelcome.text = "Chúc Mừng Năm Mới Đinh Mùi 2027!"
            }
        }
    }

    class MondayViewHolder(val itemBinding: ItemCountdownMondayBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(info: MondayCountdownInfo?) {
            if (info == null) return
            itemBinding.tvMondaysCount.text = info.mondaysRemaining.toString()
            itemBinding.tvNextMondayDate.text = info.nextMondayText
            itemBinding.tvLastMondayDate.text = info.lastMondayText
            itemBinding.tvMondaySubtitle.text = info.motivationalText

            if (info.isTodayMonday) {
                itemBinding.tvNextMondayHeader.text = itemBinding.root.context.getString(R.string.monday_today_label)
                itemBinding.tvNextMondayFooter.text = itemBinding.root.context.getString(R.string.monday_count_label)
            } else {
                itemBinding.tvNextMondayHeader.text = itemBinding.root.context.getString(R.string.monday_next_label)
                itemBinding.tvNextMondayFooter.text = itemBinding.root.context.getString(R.string.monday_early_week)
            }
        }
    }

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_TIMER else TYPE_MONDAY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TIMER) {
            val binding = ItemCountdownTimerBinding.inflate(inflater, parent, false)
            TimerViewHolder(binding)
        } else {
            val binding = ItemCountdownMondayBinding.inflate(inflater, parent, false)
            MondayViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TimerViewHolder) {
            timerViewHolder = holder
            holder.bind(currentDays, currentHours, currentMinutes, currentSeconds, isTimerFinished)
        } else if (holder is MondayViewHolder) {
            mondayViewHolder = holder
            holder.bind(currentMondayInfo)
        }
    }

    fun updateTimer(days: Long, hours: Long, minutes: Long, seconds: Long, isFinished: Boolean) {
        currentDays = days
        currentHours = hours
        currentMinutes = minutes
        currentSeconds = seconds
        isTimerFinished = isFinished
        timerViewHolder?.bind(days, hours, minutes, seconds, isFinished)
    }

    fun updateMonday(info: MondayCountdownInfo) {
        currentMondayInfo = info
        mondayViewHolder?.bind(info)
    }
}

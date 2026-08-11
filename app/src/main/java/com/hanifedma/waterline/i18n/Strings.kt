package com.hanifedma.waterline.i18n

import java.util.Locale

/**
 * Interface translations, English ⇄ Korean — the web app's `i18n.js`, ported.
 *
 * This lives in Kotlin rather than res/values-ko/ because the language is
 * switched *inside* the app, independently of the device locale, exactly as it
 * is on the web. Resource qualifiers would tie it to system settings, and the
 * notification builders (which run with no Activity anywhere) would have to
 * juggle a second Context to read them.
 *
 * Only the interface is translated. A reader's own data — clock times, logged
 * durations, calendar dates — is rendered through java.time in the chosen
 * language's locale and never passes through here.
 */
enum class Lang(val code: String, val locale: Locale) {
    EN("en", Locale.US),
    KO("ko", Locale.KOREA);

    companion object {
        /** The web app reads navigator.language; this reads the device's. */
        fun fromSystem(): Lang =
            if (Locale.getDefault().language.lowercase().startsWith("ko")) KO else EN

        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: fromSystem()
    }
}

object Strings {

    private val en: Map<String, String> = mapOf(
        "app.name" to "Waterline",
        "app.tagline" to "Fill your waterline.",

        // --- chrome ---
        "tab.timer" to "Timer",
        "tab.log" to "Log",
        "tab.body" to "Body",
        "tab.settings" to "Settings",
        "nav.signIn" to "Sign in",
        "nav.signOut" to "Sign out",
        "a11y.theme" to "Toggle colour theme",
        "a11y.lang" to "Change language",
        "a11y.account" to "Account",
        "a11y.prevMonth" to "Previous month",
        "a11y.nextMonth" to "Next month",
        "a11y.ring" to "Fasting progress",

        // --- timer ---
        "ring.ready" to "Ready when you are",
        "ring.readyMeta" to "{goal}h goal",
        "ring.left" to "{time} left",
        "ring.past" to "{time} past your {goal}h goal",
        "goal.label" to "Goal",
        "goal.option" to "{h} hours",
        "goal.locked" to "End your fast to change the goal",
        "btn.begin" to "Begin fast",
        "btn.end" to "End fast",
        "btn.editStart" to "Edit start",
        "btn.discard" to "Discard",
        "coach.until" to "{time} until {stage}",
        "coach.pastAll" to "You are past every milestone on the map.",
        "controls.startedAt" to "Started {start} · {goal}h goal at {goalAt}",

        // --- stats ---
        "stats.streak" to "Day streak",
        "stats.longest" to "Longest",
        "stats.total" to "Completed",
        "stats.hours" to "Total time",

        // --- calendar ---
        "cal.heading" to "Calendar",
        "cal.emptyHint" to "No fasts logged yet",
        "cal.summary" to "{streak}-day streak · {days} days in {month}",
        "cal.summary.one" to "{streak}-day streak · {days} day in {month}",
        "cal.fastedOn" to "Fasted on {date}",

        // --- history ---
        "history.heading" to "Your fasts",
        "history.emptyHint" to "Nothing logged yet",
        "history.empty" to "Your first fast will appear here. Start the clock whenever you're ready.",
        "history.count" to "{n} fasts logged",
        "history.count.one" to "{n} fast logged",
        "history.goalTag" to "{goal}h",
        "entry.edit" to "Edit this fast",
        "entry.delete" to "Delete this fast",
        "entry.deleteTitle" to "Delete this fast?",
        "entry.deleteConfirm" to "This can't be undone.",

        // --- stages ---
        "stages.heading" to "What happens inside you",
        "stages.subhead" to "The metabolic timeline of a water fast",
        "label.now" to "NOW",
        "stage.0.title" to "Fed state",
        "stage.0.text" to "Insulin is high and your body is busy storing what you last ate. The clock has started.",
        "stage.0.cheer" to "The fast has begun. The hardest part is already behind you.",
        "stage.4.title" to "Insulin falling",
        "stage.4.text" to "Blood sugar settles and insulin drops. Your body turns to stored glycogen for fuel.",
        "stage.4.cheer" to "Insulin is falling. Your body is switching fuel sources.",
        "stage.8.title" to "Glycogen burning",
        "stage.8.text" to "Liver glycogen is being drawn down. Fat cells begin releasing fatty acids into the blood.",
        "stage.8.cheer" to "Glycogen stores are opening up. Fat burning is warming up.",
        "stage.12.title" to "Fat burning",
        "stage.12.text" to "Glycogen runs low and lipolysis takes over. You are now running mostly on fat.",
        "stage.12.cheer" to "Twelve hours. You're officially burning fat for fuel.",
        "stage.16.title" to "Ketosis begins",
        "stage.16.text" to "The liver converts fat into ketones. Many people notice hunger fading and focus sharpening.",
        "stage.16.cheer" to "Ketosis. Your brain is starting to run on clean fuel.",
        "stage.18.title" to "Autophagy rising",
        "stage.18.text" to "Cells begin recycling damaged components — the cellular clean-up that fasting is famous for.",
        "stage.18.cheer" to "Autophagy is ramping up. Your cells are taking out the trash.",
        "stage.24.title" to "Deep ketosis",
        "stage.24.text" to "Ketones are a primary fuel. Growth hormone climbs to protect muscle while fat is burned.",
        "stage.24.cheer" to "A full day. Deep ketosis, clear head, growth hormone rising.",
        "stage.36.title" to "Growth hormone surge",
        "stage.36.text" to "Growth hormone can reach several times baseline, preserving lean tissue during the fast.",
        "stage.36.cheer" to "36 hours. Growth hormone is surging to protect your muscle.",
        "stage.48.title" to "Immune reset",
        "stage.48.text" to "Autophagy is strong and old immune cells are cleared. Electrolytes now matter a great deal.",
        "stage.48.cheer" to "Two days. Remember your electrolytes — sodium, potassium, magnesium.",
        "stage.72.title" to "Stem cell renewal",
        "stage.72.text" to "Research points to stem-cell-driven immune regeneration. Fasts this long deserve supervision.",
        "stage.72.cheer" to "Three days. This is serious territory — listen to your body.",

        // --- rotating encouragement ---
        "quote.0" to "Every fast you finish is a promise you kept to yourself.",
        "quote.1" to "Hunger comes in waves. Waves pass.",
        "quote.2" to "You are not missing a meal. You are building a habit.",
        "quote.3" to "The discomfort is temporary. The discipline is permanent.",
        "quote.4" to "Your body knows exactly what to do. Let it work.",
        "quote.5" to "Nothing tastes as good as finishing feels.",
        "quote.6" to "Water, salt, patience. That's the whole recipe.",
        "quote.7" to "You have done hard things before. This is one of them.",
        "quote.8" to "The fast doesn't get easier. You get better at it.",
        "quote.9" to "One more hour. That's all you ever have to do.",
        "quote.10" to "Stillness is doing something.",
        "quote.11" to "Fill your waterline.",

        // --- editors ---
        "modal.startTime" to "Start time",
        "modal.endTime" to "End time",
        "editor.activeTitle" to "When did you actually start?",
        "editor.activeHint" to "Forgot to hit begin? Set the real time your fast started.",
        "editor.endTitle" to "When did you break your fast?",
        "editor.endHint" to "Defaults to right now. Back-date it if you ate earlier.",
        "editor.fastTitle" to "Edit this fast",
        "editor.fastHint" to "Adjust when this fast started and when you broke it.",
        "editor.saveEnd" to "End fast",
        "editor.save" to "Save",
        "editor.setNow" to "Now",
        "editor.duration" to "That's {time}.",
        "valid.noLongerRunning" to "This fast is no longer running.",
        "valid.pickEnd" to "Pick an end time.",
        "valid.endFuture" to "A fast can't end in the future.",
        "valid.endBeforeStart" to "You can't end a fast before it started.",
        "valid.pickStart" to "Pick a start time.",
        "valid.startFuture" to "A fast can't start in the future.",
        "valid.tooOld" to "That's more than 30 days ago.",
        "valid.endAfterStart" to "The end has to come after the start.",

        // --- completion ---
        "done.goalReached" to "Goal reached",
        "done.fastLogged" to "Fast logged",
        "done.close" to "Nice",
        "done.msg.72" to "Three days on water alone. Extraordinary. Break it gently — small, simple food.",
        "done.msg.48" to "Two full days. Your body did remarkable work. Refeed slowly.",
        "done.msg.24" to "A whole day fasted. Deep ketosis, real autophagy. Well earned.",
        "done.msg.goal" to "Goal reached. That's exactly how consistency is built.",
        "done.msg.80" to "{pct}% of the way there. That's a strong fast in anyone's book.",
        "done.msg.50" to "{pct}% of your goal. Ending early is a decision, not a failure.",
        "done.msg.low" to "Logged. Listening to your body is part of the practice — come back tomorrow.",

        // --- transient messages ---
        "toast.started" to "Fast started. One hour at a time.",
        "toast.startUpdated" to "Start time updated",
        "toast.fastUpdated" to "Fast updated",
        "toast.deleted" to "Fast deleted",
        "toast.discardTitle" to "Discard this fast?",
        "toast.discardConfirm" to "It won't be logged, and the time is gone for good.",
        "toast.discarded" to "Fast discarded",
        "toast.goalReached" to "{goal}h goal reached. Anything now is a bonus.",
        "toast.merged" to "Moved {count} local records into your account.",
        "toast.merged.one" to "Moved {count} local record into your account.",
        "toast.signedOut" to "Signed out. Back to local mode.",
        "toast.notifDenied" to "Notifications are off, so the timer can't reach your lock screen.",
        "toast.notifFix" to "Fix",

        // --- sync status ---
        "status.local" to "Local mode",
        "status.localSignin" to "Local mode — sign in to sync",
        "status.syncingAs" to "Synced as {name}",
        "status.offline" to "Offline — changes will sync when you're back",
        "status.live" to "Live",

        // --- auth ---
        "err.auth.cancelled" to "Sign-in cancelled.",
        "err.auth.noAccount" to "No Google account on this device.",
        "err.auth.network" to "No connection. Local mode still works.",
        "err.auth.generic" to "Sign-in failed. Check the SHA-1 fingerprint in your Firebase project.",
        "setup.needConfig" to "Add app/google-services.json and rebuild to turn on sync.",

        // --- settings ---
        "settings.heading" to "Settings",
        "settings.account" to "Account",
        "settings.localOnly" to "This device only",
        "settings.signInHint" to "Sign in with Google to sync with the web app.",
        "settings.appearance" to "Appearance",
        "settings.theme" to "Theme",
        "settings.themeDark" to "Dark",
        "settings.themeLight" to "Light",
        "settings.language" to "Language",
        "settings.defaultGoal" to "Default goal",
        "settings.defaultGoalHint" to "What the picker starts on. A running fast keeps the goal it began with.",

        "settings.notifications" to "Notifications",
        "settings.milestones" to "Milestone alerts",
        "settings.milestonesHint" to "Ketosis, autophagy, and the moment you reach your goal.",
        "settings.reminders" to "Remind me to fast",
        "settings.remindersHint" to "A nudge when no fast is running.",
        "settings.reminderEvery" to "Nudge every",
        "settings.reminderAfterDismiss" to "Come back after I dismiss it",
        "settings.reminderAfterDismissHint" to "Swiping the reminder away silences it for this long, not forever.",
        "settings.quiet" to "Quiet hours",
        "settings.quietHint" to "Hold reminders until the morning.",
        "settings.quietFrom" to "From",
        "settings.quietTo" to "Until",
        "settings.hours" to "{n} hours",
        "settings.hours.one" to "{n} hour",
        "settings.minutes" to "{n} minutes",
        "settings.minutes.one" to "{n} minute",

        "settings.reliability" to "Keeping the timer alive",
        "settings.reliabilityHint" to "Android stops apps it thinks are idle. These three keep the fasting clock on your lock screen.",
        "settings.permNotifications" to "Show notifications",
        "settings.permNotificationsHint" to "Without this there is no timer on the lock screen at all.",
        "settings.permExact" to "Exact alarms",
        "settings.permExactHint" to "Lands milestone alerts on the minute, even in Doze.",
        "settings.permBattery" to "Unrestricted battery",
        "settings.permBatteryHint" to "Stops battery saver from killing the timer mid-fast.",
        "settings.permDnd" to "Alerts through Do Not Disturb",
        "settings.permDndHint" to "Lets milestones and the goal ring through when DND is on. The running timer is silent either way.",
        "settings.ok" to "On",
        "settings.missing" to "Off",
        "settings.grant" to "Turn on",
        "settings.allGood" to "Everything the timer needs is turned on.",

        "settings.about" to "About",
        "settings.version" to "Version",
        "settings.openWeb" to "Open the web app",
        "settings.sourceWeb" to "hanifedma.com/waterline",
        "settings.disclaimerTitle" to "Health note.",
        "settings.disclaimer" to "Waterline is an informational tool, not medical advice. Extended water fasting can be dangerous if you are pregnant or breastfeeding, underweight, diabetic, on medication, or have a history of disordered eating. Talk to a doctor before fasts longer than 24 hours, and stop immediately if you feel faint, confused, or unwell.",

        // --- notifications ---
        "notif.timer.left" to "{time} left · {goal}h goal",
        "notif.timer.leftShort" to "{time} left",
        "notif.timer.pastShort" to "{time} over",
        "notif.timer.past" to "{time} past your {goal}h goal",
        "notif.timer.sub" to "Fasting",
        "notif.action.end" to "End fast",
        "notif.action.start" to "Start {goal}h fast",
        "notif.goal.title" to "Goal reached 🏆",
        "notif.goal.body" to "You hit your {goal}-hour goal. Anything now is a bonus.",
        "notif.reminder.title" to "Ready to fast?",
        "notif.reminder.0" to "The clock isn't running. Begin whenever you're ready.",
        "notif.reminder.1" to "Your last fast was {time} ago. Another one?",
        "notif.reminder.2" to "A {goal}-hour fast started now finishes at {at}.",
        "notif.reminder.3" to "One tap and the ring starts filling again.",
        "notif.done.title" to "Fast logged",
        "notif.done.body" to "{time} · {msg}",

        // --- common ---
        "common.ok" to "OK",
        "common.cancel" to "Cancel",
        "common.save" to "Save",
        "common.delete" to "Delete",
        "common.close" to "Close",
        "common.done" to "Done",
        "common.on" to "On",
        "common.off" to "Off",
    )

    private val ko: Map<String, String> = mapOf(
        "app.name" to "Waterline",
        "app.tagline" to "당신의 수위를 채우세요.",

        // --- chrome ---
        "tab.timer" to "타이머",
        "tab.log" to "기록",
        "tab.body" to "몸",
        "tab.settings" to "설정",
        "nav.signIn" to "로그인",
        "nav.signOut" to "로그아웃",
        "a11y.theme" to "색상 테마 전환",
        "a11y.lang" to "언어 변경",
        "a11y.account" to "계정",
        "a11y.prevMonth" to "이전 달",
        "a11y.nextMonth" to "다음 달",
        "a11y.ring" to "단식 진행률",

        // --- timer ---
        "ring.ready" to "준비되면 시작하세요",
        "ring.readyMeta" to "목표 {goal}시간",
        "ring.left" to "{time} 남음",
        "ring.past" to "{goal}시간 목표에서 {time} 초과",
        "goal.label" to "목표",
        "goal.option" to "{h}시간",
        "goal.locked" to "목표를 바꾸려면 단식을 종료하세요",
        "btn.begin" to "단식 시작",
        "btn.end" to "단식 종료",
        "btn.editStart" to "시작 시간 수정",
        "btn.discard" to "취소",
        "coach.until" to "{stage}까지 {time} 남음",
        "coach.pastAll" to "지도의 모든 단계를 지나왔습니다.",
        "controls.startedAt" to "{start} 시작 · 목표 {goal}시간, {goalAt} 도달",

        // --- stats ---
        "stats.streak" to "연속 일수",
        "stats.longest" to "최장 기록",
        "stats.total" to "완료",
        "stats.hours" to "누적 시간",

        // --- calendar ---
        "cal.heading" to "달력",
        "cal.emptyHint" to "아직 기록된 단식이 없습니다",
        "cal.summary" to "{streak}일 연속 · {month}에 {days}일",
        "cal.summary.one" to "{streak}일 연속 · {month}에 {days}일",
        "cal.fastedOn" to "{date} 단식함",

        // --- history ---
        "history.heading" to "나의 단식 기록",
        "history.emptyHint" to "아직 기록이 없습니다",
        "history.empty" to "첫 단식이 여기에 표시됩니다. 준비되면 언제든 시작하세요.",
        "history.count" to "{n}개 기록됨",
        "history.count.one" to "{n}개 기록됨",
        "history.goalTag" to "{goal}시간",
        "entry.edit" to "이 단식 수정",
        "entry.delete" to "이 단식 삭제",
        "entry.deleteTitle" to "이 단식을 삭제할까요?",
        "entry.deleteConfirm" to "되돌릴 수 없습니다.",

        // --- stages ---
        "stages.heading" to "몸속에서 일어나는 일",
        "stages.subhead" to "물 단식의 대사 타임라인",
        "label.now" to "지금",
        "stage.0.title" to "식후 상태",
        "stage.0.text" to "인슐린이 높고 몸은 방금 먹은 것을 저장하느라 바쁩니다. 시계가 시작되었습니다.",
        "stage.0.cheer" to "단식이 시작됐어요. 가장 힘든 부분은 이미 지났습니다.",
        "stage.4.title" to "인슐린 감소",
        "stage.4.text" to "혈당이 안정되고 인슐린이 떨어집니다. 몸이 저장된 글리코겐을 연료로 쓰기 시작합니다.",
        "stage.4.cheer" to "인슐린이 떨어지고 있어요. 몸이 연료원을 바꾸는 중입니다.",
        "stage.8.title" to "글리코겐 연소",
        "stage.8.text" to "간 글리코겐이 소모됩니다. 지방 세포가 지방산을 혈액으로 내보내기 시작합니다.",
        "stage.8.cheer" to "글리코겐 저장고가 열리고 있어요. 지방 연소가 예열되는 중입니다.",
        "stage.12.title" to "지방 연소",
        "stage.12.text" to "글리코겐이 바닥나고 지방 분해가 주가 됩니다. 이제 주로 지방을 태우고 있습니다.",
        "stage.12.cheer" to "12시간. 이제 공식적으로 지방을 연료로 태우고 있어요.",
        "stage.16.title" to "케토시스 시작",
        "stage.16.text" to "간이 지방을 케톤으로 바꿉니다. 배고픔이 사라지고 집중력이 또렷해지는 걸 느끼는 사람이 많습니다.",
        "stage.16.cheer" to "케토시스. 뇌가 깨끗한 연료로 돌아가기 시작합니다.",
        "stage.18.title" to "자가포식 상승",
        "stage.18.text" to "세포가 손상된 부분을 재활용하기 시작합니다 — 단식으로 유명한 세포 청소입니다.",
        "stage.18.cheer" to "자가포식이 활발해지고 있어요. 세포가 쓰레기를 내다 버리는 중입니다.",
        "stage.24.title" to "깊은 케토시스",
        "stage.24.text" to "케톤이 주 연료가 됩니다. 성장호르몬이 올라 근육을 보호하며 지방을 태웁니다.",
        "stage.24.cheer" to "꼬박 하루. 깊은 케토시스, 맑은 정신, 성장호르몬 상승.",
        "stage.36.title" to "성장호르몬 급증",
        "stage.36.text" to "성장호르몬이 기준치의 몇 배까지 오를 수 있어 단식 중 제지방을 지킵니다.",
        "stage.36.cheer" to "36시간. 성장호르몬이 급증해 근육을 지키고 있어요.",
        "stage.48.title" to "면역 리셋",
        "stage.48.text" to "자가포식이 강하고 오래된 면역세포가 정리됩니다. 이제 전해질이 매우 중요합니다.",
        "stage.48.cheer" to "이틀. 전해질을 잊지 마세요 — 나트륨, 칼륨, 마그네슘.",
        "stage.72.title" to "줄기세포 재생",
        "stage.72.text" to "연구에 따르면 줄기세포 주도의 면역 재생이 일어납니다. 이 정도로 긴 단식은 전문가의 관리가 필요합니다.",
        "stage.72.cheer" to "3일. 이건 진지한 영역이에요 — 몸의 소리에 귀 기울이세요.",

        // --- rotating encouragement ---
        "quote.0" to "당신이 끝낸 모든 단식은 스스로와 지킨 약속입니다.",
        "quote.1" to "배고픔은 파도처럼 옵니다. 파도는 지나갑니다.",
        "quote.2" to "끼니를 거르는 게 아니라 습관을 만드는 겁니다.",
        "quote.3" to "불편함은 잠깐이고, 절제는 남습니다.",
        "quote.4" to "당신의 몸은 무엇을 해야 할지 정확히 압니다. 맡기세요.",
        "quote.5" to "그 어떤 맛도 해냈을 때의 기분만큼 좋지 않습니다.",
        "quote.6" to "물, 소금, 인내. 그게 레시피의 전부예요.",
        "quote.7" to "당신은 전에도 힘든 일을 해냈습니다. 이것도 그중 하나예요.",
        "quote.8" to "단식이 쉬워지는 게 아니라, 당신이 더 능숙해지는 겁니다.",
        "quote.9" to "한 시간만 더. 당신이 해야 할 건 늘 그것뿐이에요.",
        "quote.10" to "가만히 있는 것도 무언가를 하는 것입니다.",
        "quote.11" to "당신의 수위를 채우세요.",

        // --- editors ---
        "modal.startTime" to "시작 시간",
        "modal.endTime" to "종료 시간",
        "editor.activeTitle" to "실제로 언제 시작했나요?",
        "editor.activeHint" to "시작 버튼을 깜빡했나요? 단식이 시작된 실제 시간을 입력하세요.",
        "editor.endTitle" to "언제 단식을 중단했나요?",
        "editor.endHint" to "기본값은 지금입니다. 더 일찍 먹었다면 이전 시간으로 지정하세요.",
        "editor.fastTitle" to "이 단식 수정",
        "editor.fastHint" to "이 단식의 시작과 종료 시간을 조정하세요.",
        "editor.saveEnd" to "단식 종료",
        "editor.save" to "저장",
        "editor.setNow" to "지금",
        "editor.duration" to "{time}입니다.",
        "valid.noLongerRunning" to "이 단식은 더 이상 진행 중이 아닙니다.",
        "valid.pickEnd" to "종료 시간을 선택하세요.",
        "valid.endFuture" to "단식은 미래에 종료될 수 없습니다.",
        "valid.endBeforeStart" to "단식은 시작 전에 종료할 수 없습니다.",
        "valid.pickStart" to "시작 시간을 선택하세요.",
        "valid.startFuture" to "단식은 미래에 시작될 수 없습니다.",
        "valid.tooOld" to "30일보다 이전입니다.",
        "valid.endAfterStart" to "종료 시간은 시작 이후여야 합니다.",

        // --- completion ---
        "done.goalReached" to "목표 달성",
        "done.fastLogged" to "단식 기록됨",
        "done.close" to "좋아요",
        "done.msg.72" to "물만 마신 3일. 놀랍습니다. 작고 단순한 음식으로 부드럽게 보식하세요.",
        "done.msg.48" to "꼬박 이틀. 몸이 대단한 일을 해냈어요. 천천히 보식하세요.",
        "done.msg.24" to "하루 종일 단식. 깊은 케토시스와 진짜 자가포식. 충분히 해냈어요.",
        "done.msg.goal" to "목표 달성. 바로 이렇게 꾸준함이 만들어집니다.",
        "done.msg.80" to "{pct}% 도달. 누가 봐도 훌륭한 단식이에요.",
        "done.msg.50" to "목표의 {pct}%. 일찍 끝내는 것도 실패가 아니라 선택입니다.",
        "done.msg.low" to "기록했어요. 몸의 소리를 듣는 것도 연습의 일부예요 — 내일 다시 만나요.",

        // --- transient messages ---
        "toast.started" to "단식을 시작했어요. 한 번에 한 시간씩.",
        "toast.startUpdated" to "시작 시간이 업데이트되었습니다",
        "toast.fastUpdated" to "단식이 업데이트되었습니다",
        "toast.deleted" to "단식이 삭제되었습니다",
        "toast.discardTitle" to "이 단식을 취소할까요?",
        "toast.discardConfirm" to "기록되지 않으며 시간도 되돌릴 수 없습니다.",
        "toast.discarded" to "단식을 취소했습니다",
        "toast.goalReached" to "{goal}시간 목표 달성. 지금부터는 전부 보너스예요.",
        "toast.merged" to "{count}개의 로컬 기록을 계정으로 옮겼습니다.",
        "toast.merged.one" to "{count}개의 로컬 기록을 계정으로 옮겼습니다.",
        "toast.signedOut" to "로그아웃되었습니다. 로컬 모드로 돌아갑니다.",
        "toast.notifDenied" to "알림이 꺼져 있어 잠금 화면에 타이머를 표시할 수 없습니다.",
        "toast.notifFix" to "설정",

        // --- sync status ---
        "status.local" to "로컬 모드",
        "status.localSignin" to "로컬 모드 — 로그인하면 동기화됩니다",
        "status.syncingAs" to "{name}(으)로 동기화 중",
        "status.offline" to "오프라인 — 연결되면 동기화됩니다",
        "status.live" to "실시간",

        // --- auth ---
        "err.auth.cancelled" to "로그인을 취소했습니다.",
        "err.auth.noAccount" to "이 기기에 Google 계정이 없습니다.",
        "err.auth.network" to "연결할 수 없습니다. 로컬 모드는 계속 작동합니다.",
        "err.auth.generic" to "로그인에 실패했습니다. Firebase 프로젝트의 SHA-1 지문을 확인하세요.",
        "setup.needConfig" to "동기화를 사용하려면 app/google-services.json을 추가하고 다시 빌드하세요.",

        // --- settings ---
        "settings.heading" to "설정",
        "settings.account" to "계정",
        "settings.localOnly" to "이 기기에만 저장",
        "settings.signInHint" to "Google로 로그인하면 웹 앱과 동기화됩니다.",
        "settings.appearance" to "화면",
        "settings.theme" to "테마",
        "settings.themeDark" to "어둡게",
        "settings.themeLight" to "밝게",
        "settings.language" to "언어",
        "settings.defaultGoal" to "기본 목표",
        "settings.defaultGoalHint" to "선택기의 기본값입니다. 진행 중인 단식은 시작할 때의 목표를 유지합니다.",

        "settings.notifications" to "알림",
        "settings.milestones" to "단계 알림",
        "settings.milestonesHint" to "케토시스, 자가포식, 그리고 목표 달성 순간.",
        "settings.reminders" to "단식 알림 받기",
        "settings.remindersHint" to "단식이 진행 중이 아닐 때 알려드립니다.",
        "settings.reminderEvery" to "알림 주기",
        "settings.reminderAfterDismiss" to "지운 뒤 다시 알림",
        "settings.reminderAfterDismissHint" to "알림을 지워도 영원히 사라지지 않고 이 시간 뒤에 다시 옵니다.",
        "settings.quiet" to "방해 금지 시간",
        "settings.quietHint" to "이 시간대에는 아침까지 알림을 미룹니다.",
        "settings.quietFrom" to "시작",
        "settings.quietTo" to "종료",
        "settings.hours" to "{n}시간",
        "settings.hours.one" to "{n}시간",
        "settings.minutes" to "{n}분",
        "settings.minutes.one" to "{n}분",

        "settings.reliability" to "타이머를 살려 두기",
        "settings.reliabilityHint" to "안드로이드는 쉬고 있다고 판단한 앱을 정지시킵니다. 아래 세 가지가 잠금 화면의 단식 시계를 지켜 줍니다.",
        "settings.permNotifications" to "알림 표시",
        "settings.permNotificationsHint" to "이것이 없으면 잠금 화면에 타이머가 아예 표시되지 않습니다.",
        "settings.permExact" to "정확한 알람",
        "settings.permExactHint" to "절전 모드에서도 단계 알림이 제시간에 도착합니다.",
        "settings.permBattery" to "배터리 제한 없음",
        "settings.permBatteryHint" to "절전 모드가 단식 중 타이머를 종료하지 못하게 합니다.",
        "settings.permDnd" to "방해 금지 중에도 알림",
        "settings.permDndHint" to "방해 금지 모드에서도 단계·목표 알림이 표시됩니다. 진행 중 타이머는 어느 쪽이든 무음입니다.",
        "settings.ok" to "켜짐",
        "settings.missing" to "꺼짐",
        "settings.grant" to "켜기",
        "settings.allGood" to "타이머에 필요한 설정이 모두 켜져 있습니다.",

        "settings.about" to "정보",
        "settings.version" to "버전",
        "settings.openWeb" to "웹 앱 열기",
        "settings.sourceWeb" to "hanifedma.com/waterline",
        "settings.disclaimerTitle" to "건강 유의사항.",
        "settings.disclaimer" to "Waterline은 정보 제공 도구이며 의학적 조언이 아닙니다. 임신·수유 중이거나 저체중, 당뇨, 복약 중이거나 섭식 장애 이력이 있다면 장시간 물 단식은 위험할 수 있습니다. 24시간이 넘는 단식 전에는 의사와 상담하고, 어지럽거나 혼란스럽거나 몸이 좋지 않으면 즉시 중단하세요.",

        // --- notifications ---
        "notif.timer.left" to "{time} 남음 · 목표 {goal}시간",
        "notif.timer.leftShort" to "{time} 남음",
        "notif.timer.pastShort" to "{time} 초과",
        "notif.timer.past" to "{goal}시간 목표에서 {time} 초과",
        "notif.timer.sub" to "단식 중",
        "notif.action.end" to "단식 종료",
        "notif.action.start" to "{goal}시간 단식 시작",
        "notif.goal.title" to "목표 달성 🏆",
        "notif.goal.body" to "{goal}시간 목표를 달성했어요. 지금부터는 전부 보너스입니다.",
        "notif.reminder.title" to "단식할 준비 되셨나요?",
        "notif.reminder.0" to "시계가 멈춰 있어요. 준비되면 시작하세요.",
        "notif.reminder.1" to "마지막 단식이 {time} 전이에요. 한 번 더 해볼까요?",
        "notif.reminder.2" to "지금 {goal}시간 단식을 시작하면 {at}에 끝납니다.",
        "notif.reminder.3" to "한 번만 누르면 링이 다시 차오릅니다.",
        "notif.done.title" to "단식 기록됨",
        "notif.done.body" to "{time} · {msg}",

        // --- common ---
        "common.ok" to "확인",
        "common.cancel" to "취소",
        "common.save" to "저장",
        "common.delete" to "삭제",
        "common.close" to "닫기",
        "common.done" to "완료",
        "common.on" to "켜짐",
        "common.off" to "꺼짐",
    )

    private fun table(lang: Lang) = if (lang == Lang.KO) ko else en

    /** Translate. `t(lang, "ring.left", "time" to "3h 20m")`. */
    fun t(lang: Lang, key: String, vararg params: Pair<String, Any>): String {
        // Fall back through English, then the key itself, so a missing string
        // is visible in review but never crashes or shows null.
        var s = table(lang)[key] ?: en[key] ?: key
        for ((name, value) in params) s = s.replace("{$name}", value.toString())
        return s
    }

    /**
     * A string that counts something: "1 fast" vs "2 fasts".
     *
     * English needs a singular form; without one the history header reads
     * "1 fasts logged", which is the very first thing anyone sees after their
     * first fast. Korean has no plural, so both keys hold the same text there —
     * defined rather than left to fall back, because the fallback would reach
     * the English table and print English inside a Korean UI.
     */
    fun tCount(lang: Lang, key: String, n: Int, vararg params: Pair<String, Any>): String =
        t(lang, if (n == 1) "$key.one" else key, "n" to n, *params)
}

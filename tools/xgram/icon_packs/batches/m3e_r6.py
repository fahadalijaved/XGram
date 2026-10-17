# XGram Line / Duotone batch "m3e_r6": glyphs Material 3 Expressive round 6 added, drawn as the Line family so every
# pack has its own (the library packs take theirs from maps/<pack>.json): the schedule sheet's "In 8 hours", a Mac
# laptop in the Devices list, the SMS code page, the passcode screen capture row and the open lock of the app lock.
# "In 2 hours" and the auto-lock row are the timer of batch "chat", the email pages its mail, the dropdown arrows the
# small arrow of batch "lists"; "In 30 minutes" and "In a year" are the core time and calendar.
from dsl import *
from dsl import core

BUBBLE = core.ICONS['chat_bubble'][0]

ICONS = {
    # an hourglass: the glass is one closed shape (the Duotone body), capped top and bottom
    'hourglass': [L(6.5, 3, 17.5, 3), L(6.5, 21, 17.5, 21),
                  P('M8 3C8 7.5 12 8.5 12 12C12 15.5 8 16.5 8 21H16C16 16.5 12 15.5 12 12C12 8.5 16 7.5 16 3Z')],
    # a laptop: the screen with rounded top corners over a base line wider than it (as device_desktop's rounding)
    'device_laptop': [P('M6.5 5.5h11A2.5 2.5 0 0 1 20 8v8H4V8A2.5 2.5 0 0 1 6.5 5.5Z'), L(2.5, 19, 21.5, 19)],
    # an SMS: the chat bubble with three dots
    'sms': [BUBBLE, DOT(8.2, 10.5, 1.2), DOT(12, 10.5, 1.2), DOT(15.8, 10.5, 1.2)],
    # a screenshot: a phone with the four corners of a capture inside its screen
    'screenshot': [RR(5.5, 2, 13, 20, 2.5), PL((9, 9.5), (9, 7.5), (11, 7.5)), PL((13, 7.5), (15, 7.5), (15, 9.5)),
                   PL((9, 14.5), (9, 16.5), (11, 16.5)), PL((15, 14.5), (15, 16.5), (13, 16.5))],
    # the core lock with its shackle up and open on the right
    'lock_open': [RR(5, 10.5, 14, 10, 2.5), P('M8 10.5V7.5a4 4 0 0 1 7.6-1.75'), L(12, 14.5, 12, 16.5)],
}

MAP = {
    'dotvhs_baseline_time_30m_24': 'time',
    'dotvhs_baseline_time_1y_24': 'calendar',
    'dotvhs_baseline_time_8h_24': 'hourglass',
    'xgram_r6_b1_hourglass_24': 'hourglass',
    'xgram_m3e_r6_b2_device_laptop_24': 'device_laptop',
    'xgram_r6_b8_sms_24': 'sms',
    'xgram_r6_b8_screenshot_24': 'screenshot',
    'xgram_m3e_r6_b2_lock_open_24': 'lock_open',
}

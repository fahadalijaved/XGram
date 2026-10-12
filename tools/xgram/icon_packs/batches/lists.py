# XGram Line batch "lists": arrows and chevrons, folders, chat-list filters, check boxes, cards, and maps.
from dsl import *

# The core folder (tools/xgram/icons_line.py) and variations of it.
FOLDER = 'M3 7.5A2.5 2.5 0 0 1 5.5 5h3.6l2.2 2.5h7.2A2.5 2.5 0 0 1 21 10v7.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-10Z'
# The folder with its bottom-right corner bitten out around a badge centred on (18, 18).
FOLDER_BITE = 'M3 7.5A2.5 2.5 0 0 1 5.5 5h3.6l2.2 2.5h7.2A2.5 2.5 0 0 1 21 10v4A5 5 0 0 0 13.4 20H5.5A2.5 2.5 0 0 1 3 17.5v-10Z'
# Two stacked folders: the front one and the edge of the one behind it.
FOLDER_FRONT = 'M6.75 6.25A2 2 0 0 1 8.75 4.25h3l2 2.2h5.5a2 2 0 0 1 2 2v6.3a2 2 0 0 1-2 2h-10.5a2 2 0 0 1-2-2v-8.5Z'
FOLDER_BACK = 'M2.75 8.25v9a2.5 2.5 0 0 0 2.5 2.5h11.5'

# A funnel of filter lines; the chat type it filters by sits below them as the narrowest step.
FILTER = [L(3, 4, 21, 4), L(6.5, 9, 17.5, 9)]


def _puzzle(x, y, w, h):
    """Puzzle piece: a w x h rounded square at (x, y) with a tab on top and one on the right."""
    tx = x + w / 2 - 1.5  # top tab neck, 3 wide
    ty = y + h / 2 - 1.5  # right tab neck, 3 high
    return ('M%s %sH%sV%sa2.25 2.25 0 1 1 3 0V%sH%sa2.5 2.5 0 0 1 2.5 2.5V%sH%sa2.25 2.25 0 1 1 0 3H%sV%s'
            'a2.5 2.5 0 0 1-2.5 2.5H%sa2.5 2.5 0 0 1-2.5-2.5V%sa2.5 2.5 0 0 1 2.5-2.5Z') % (
        f(x + 2.5), f(y), f(tx), f(y - 0.5), f(y), f(x + w - 2.5), f(ty), f(x + w + 0.5), f(x + w), f(y + h - 2.5),
        f(x + 2.5), f(y + 2.5))


ICONS = {
    # arrows and chevrons
    'arrow_back': [L(19.5, 12, 5, 12), PL((11, 6), (5, 12), (11, 18))],
    'arrow_downward': [L(12, 4.5, 12, 19), PL((6, 13), (12, 19), (18, 13))],
    'keyboard_arrow_down': [PL((6, 9), (12, 15), (18, 9))],
    'keyboard_arrow_up': [PL((6, 15), (12, 9), (18, 15))],
    'keyboard_arrow_left': [PL((15, 6), (9, 12), (15, 18))],
    'keyboard_arrow_right': [PL((9, 6), (15, 12), (9, 18))],
    'small_arrow_down': [PL((8, 10), (12, 14), (16, 10))],
    'swap_horiz': [L(8, 8, 20, 8), PL((16.5, 4.5), (20, 8), (16.5, 11.5)), L(16, 16, 4, 16), PL((7.5, 12.5), (4, 16), (7.5, 19.5))],
    'recent': [P('M4.1 13.5A8 8 0 1 0 6.3 6.3L4 8.5'), PL((4, 4.5), (4, 8.5), (8, 8.5)), PL((12, 8), (12, 12.3), (15, 14.1))],
    'import': [RR(9, 3.5, 11.5, 17, 2.5), L(3.5, 12, 14.5, 12), PL((11, 8.5), (14.5, 12), (11, 15.5))],
    'source_merge': [C(6.5, 5, 2.3), C(6.5, 19, 2.3), C(17.5, 13.5, 2.3), L(6.5, 7.3, 6.5, 16.7), P('M6.5 7.3V8.5a5 5 0 0 0 5 5h3.7')],
    # folders
    'folder_plus': [P(FOLDER_BITE), L(18, 15.4, 18, 20.6), L(15.4, 18, 20.6, 18)],
    'folder_remove': [P(FOLDER_BITE), L(16, 16, 20, 20), L(20, 16, 16, 20)],
    'create_new_folder': [P(FOLDER), L(12, 10.8, 12, 16.8), L(9, 13.8, 15, 13.8)],
    'folder_open': [P('M3 20V7.5A2.5 2.5 0 0 1 5.5 5h3.6l2.2 2.5h5.2A2.5 2.5 0 0 1 19 10v1'),
                    P('M3 20l3.2-8a1.5 1.5 0 0 1 1.4-1h12.6a1 1 0 0 1 .9 1.4l-2.9 6.6a1.6 1.6 0 0 1-1.5 1H3Z')],
    'folder_copy': [P(FOLDER_FRONT), P(FOLDER_BACK)],
    # the pencil is cut out of the front folder's fill in Duotone
    'edit_folders': [P(FOLDER_FRONT), P(FOLDER_BACK), P('M15.6 8.4l1.9 1.9-4.6 4.6h-1.9V13l4.6-4.6Z')],
    'folders_reorder': [RR(3, 3.5, 7.5, 6.5, 2), RR(3, 14, 7.5, 6.5, 2), P('M13.5 6.75h2a5 5 0 0 1 0 10.5h-2'), PL((16, 14.75), (13.5, 17.25), (16, 19.75))],
    # chat-list filters
    'filter_variant_remove': FILTER + [L(9.5, 14, 14.5, 19), L(14.5, 14, 9.5, 19)],
    'filter_variant_user': FILTER + [C(12, 13.8, 1.8), P('M7.8 21a4.2 2.8 0 0 1 8.4 0Z')],
    'filter_variant_group': FILTER + [C(10, 14, 1.7), P('M6 21a4 2.6 0 0 1 8 0Z'), P('M14.8 12.3a1.7 1.7 0 0 1 0 3.4'), P('M16.6 18.3c1 .5 1.6 1.4 1.6 2.7')],
    'filter_variant_channel': FILTER + [P('M6.8 15.9v1.6a.8.8 0 0 0 .8.8H9l5 2.4V12.7L9 15.1H7.6a.8.8 0 0 0-.8.8Z'), P('M16.6 14.9a2.6 2.6 0 0 1 0 3.6')],
    'filter_variant_bot': FILTER + [RR(7.8, 14.5, 8.4, 6.5, 2.3), L(12, 14.5, 12, 13.1), DOT(12, 12.3, 1), DOT(10.3, 17.7, 0.95), DOT(13.7, 17.7, 0.95)],
    'filter_variant_unread': FILTER + [C(12, 16.5, 3)],
    # check boxes, lists and cards
    'check_box_outline_blank': [RR(3.5, 3.5, 17, 17, 2.5)],
    'indeterminate_check_box': [RR(3.5, 3.5, 17, 17, 2.5), L(8, 12, 16, 12)],
    'ballot': [RR(3.5, 3.5, 17, 17, 2.5), DOT(8, 9, 1.3), L(11, 9, 16.5, 9), DOT(8, 15, 1.3), L(11, 15, 16.5, 15)],
    'receipt': [P('M7 3h10a2 2 0 0 1 2 2v16l-2.3-1.6-2.4 1.6-2.3-1.6-2.3 1.6-2.4-1.6L5 21V5a2 2 0 0 1 2-2Z'),
                L(9, 7.5, 15, 7.5), L(9, 11, 15, 11), L(9, 14.5, 13, 14.5)],
    'assignment': [P('M7 4.5h2V4a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 4v.5h2A2.5 2.5 0 0 1 19.5 7v12a2.5 2.5 0 0 1-2.5 2.5H7A2.5 2.5 0 0 1 4.5 19V7A2.5 2.5 0 0 1 7 4.5Z'),
                   P('M9 4.5v.8a1.2 1.2 0 0 0 1.2 1.2h3.6a1.2 1.2 0 0 0 1.2-1.2v-.8'), L(8.5, 11, 15.5, 11), L(8.5, 14.5, 15.5, 14.5), L(8.5, 18, 12.5, 18)],
    'clear_all': [L(7, 7, 20.5, 7), L(5.25, 12, 18.75, 12), L(3.5, 17, 17, 17)],
    'label': [P('M5.5 6h10.3a2 2 0 0 1 1.6.8l3.8 5.2-3.8 5.2a2 2 0 0 1-1.6.8H5.5A2.5 2.5 0 0 1 3 15.5v-7A2.5 2.5 0 0 1 5.5 6Z')],
    'toggle_on': [RR(2.5, 7, 19, 10, 5), DOT(16.5, 12, 2.7)],
    # a grid of app tiles (squares, like Telegram X's); a 3 x 3 grid of dots is the PIN pad of system's lock_pin
    'apps': [RR(x, y, 6.6, 6.6, 2) for y in (3.6, 13.8) for x in (3.6, 13.8)],
    'extension': [P(_puzzle(3, 7.7, 13.5, 13))],
    'layers': [PL((12, 4.75), (21, 9.75), (12, 14.75), (3, 9.75), close=True), PL((3, 14.25), (12, 19.25), (21, 14.25))],
    'gesture': [P('M4.5 8.1c2-2.6 5-3.6 5.8-2.1 1.2 2.4-6 7.2-4.6 10.3 1.3 2.8 6-3.6 8.6-4.6 1.6-.6 2.4.4 1.6 2.4-.8 2-2 4.3-.4 4.6 1.3.3 2.8-1.4 4-3')],
    # maps and places
    'gps_fixed': [C(12, 12, 6.5), DOT(12, 12, 2.3), L(12, 2.5, 12, 5.5), L(12, 18.5, 12, 21.5), L(2.5, 12, 5.5, 12), L(18.5, 12, 21.5, 12)],
    'ic_map_pin': [C(12, 8.5, 5.5), P('M9.8 13.6 12 21l2.2-7.4')],
    'map': [PL((3.5, 5.8), (9, 3.8), (15, 5.8), (20.5, 3.8), (20.5, 18.2), (15, 20.2), (9, 18.2), (3.5, 20.2), close=True),
            L(9, 3.8, 9, 18.2), L(15, 5.8, 15, 20.2)],
    'directions': [P('M10.6 3.4a2 2 0 0 1 2.8 0l7.2 7.2a2 2 0 0 1 0 2.8l-7.2 7.2a2 2 0 0 1-2.8 0l-7.2-7.2a2 2 0 0 1 0-2.8Z'),
                   P('M9 15.5V13a2 2 0 0 1 2-2h4.5'), PL((13, 8.5), (15.5, 11), (13, 13.5))],
    # the needle is cut out of the dial's fill in Duotone
    'explore': [C(12, 12, 9), P('M15.8 8.2l-2.3 5.3-5.3 2.3 2.3-5.3 5.3-2.3Z')],
    'directions_car': [RR(3, 10, 18, 7.5, 2.5), P('M5 10l1.6-4.1A2 2 0 0 1 8.5 4.5h7a2 2 0 0 1 1.9 1.4L19 10'),
                       L(6.5, 17.5, 6.5, 19.5), L(17.5, 17.5, 17.5, 19.5), DOT(7, 13.7, 1.3), DOT(17, 13.7, 1.3)],
}

MAP = {
    'baseline_gps_fixed_24': 'gps_fixed', 'baseline_gps_fixed_16': 'gps_fixed', 'baseline_gps_fixed_12': 'gps_fixed',
    'baseline_keyboard_arrow_down_20': 'keyboard_arrow_down', 'baseline_keyboard_arrow_down_24': 'keyboard_arrow_down',
    'baseline_directions_car_24': 'directions_car',
    'round_keyboard_arrow_right_24': 'keyboard_arrow_right', 'round_keyboard_arrow_right_18': 'keyboard_arrow_right',
    'round_keyboard_arrow_right_16': 'keyboard_arrow_right',
    'baseline_apps_24': 'apps',
    'baseline_directions_24': 'directions',
    'baseline_filter_variant_remove_24': 'filter_variant_remove',
    'baseline_extension_24': 'extension',
    'baseline_map_24': 'map',
    'baseline_arrow_back_24': 'arrow_back',
    'templarian_baseline_folder_plus_24': 'folder_plus',
    'templarian_baseline_folder_remove_24': 'folder_remove',
    'baseline_check_box_outline_blank_24': 'check_box_outline_blank',
    'baseline_arrow_downward_24': 'arrow_downward',
    'templarian_baseline_source_merge_24': 'source_merge',
    'baseline_small_arrow_down_18': 'small_arrow_down', 'baseline_small_arrow_down_24': 'small_arrow_down',
    'baseline_keyboard_arrow_left_24': 'keyboard_arrow_left', 'round_keyboard_arrow_left_24': 'keyboard_arrow_left',
    'ic_map_pin_44': 'ic_map_pin',
    'baseline_ballot_16': 'ballot',
    'baseline_receipt_16': 'receipt',
    'baseline_assignment_24': 'assignment',
    'templarian_baseline_import_16': 'import',
    'outline_toggle_on_24': 'toggle_on',
    'baseline_clear_all_24': 'clear_all',
    'baseline_create_new_folder_24': 'create_new_folder',
    'baseline_folder_open_96': 'folder_open',
    'baseline_label_24': 'label',
    'baseline_indeterminate_check_box_24': 'indeterminate_check_box',
    'baseline_filter_variant_user_18': 'filter_variant_user',
    'baseline_filter_variant_group_18': 'filter_variant_group',
    'baseline_filter_variant_channel_18': 'filter_variant_channel',
    'baseline_filter_variant_bot_18': 'filter_variant_bot',
    'baseline_filter_variant_unread_18': 'filter_variant_unread',
    'baseline_edit_folders_24': 'edit_folders',
    'baseline_layers_24': 'layers',
    'baseline_explore_24': 'explore',
    'baseline_keyboard_arrow_up_24': 'keyboard_arrow_up',
    'baseline_gesture_24': 'gesture',
    'baseline_folder_copy_24': 'folder_copy',
    'dotvhs_baseline_folders_reorder_24': 'folders_reorder',
    'baseline_swap_horiz_24': 'swap_horiz',
    'baseline_recent_24': 'recent',
}

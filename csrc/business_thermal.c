/* Thermal discovery, trusted/suspect sources, hysteresis and cooldown.
 * Preserves the original raw temperature normalization and IO call order. */
#include "business_support.h"



int32_t ios_business_thermal(IosState *state, DomainIo *context, int32_t requested) {
  DomainIo io = *context;
  uint32_t requested_br = (uint32_t)requested;
  uint64_t settle_until_ms;
  bool confirmed;
  uint32_t limited_br;
  int read_result;
  FILE *stream;
  char *reason_text;
  DIR *directory;
  dirent *entry;
  char *text_cursor;
  uint64_t refresh_interval_ms;
  uint32_t type_prefix;
  uint32_t sample_or_floor;
  uint32_t trigger_or_display;
  uint64_t read_timestamp_ms;
  uint32_t suspect_temperature;
  uint32_t selected_temperature;
  int64_t zone_index;
  uint32_t resume_or_charger;
  uint32_t battery_temperature;
  int64_t zone_offset;
  float upper_fraction;
  float settle_fraction;
  float bounded_fraction;
  uint32_t trusted_temperature;
  uint32_t temperature_scratch;
  _Alignas(8) char scratch[256] = {0};
  char thermal_type[64] = {0};
  char thermal_source[32] = {0};
  state->g_thermal_entry = 0;
  state->g_thermal_source_filtered = 0;
  state->g_thermal_emergency_bypass = 0;
  state->g_thermal_cap_br = 0;
  limited_br = requested_br;
  if (state->cfg_thermal_cap_enable == 0) {
    state->g_heat_guard_active = 0;
    state->g_thermal_guard_confirm_cnt = 0;
    memcpy(state->g_thermal_guard_source, "-\000", 2);
    state->g_thermal_exit_settle_until = 0;
  }
  else {
    clock_gettime(1,(timespec *)scratch);
    if ((state->g_heat_guard_active & 1) == 0) {
      refresh_interval_ms = 15000;
      if (state->cfg_thermal_hard_resume + -5000 <= state->g_thermal_temp) {
        refresh_interval_ms = 10000;
      }
    }
    else {
      refresh_interval_ms = 5000;
    }
    read_timestamp_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
    if ((state->g_last_thermal_temp_read_ms == 0) ||
       (selected_temperature = state->g_thermal_temp, trigger_or_display = state->cfg_thermal_hard_trigger,
       resume_or_charger = state->cfg_thermal_hard_resume, trusted_temperature = state->g_thermal_trusted_temp_mdeg,
       suspect_temperature = state->g_thermal_suspect_temp_mdeg,
       refresh_interval_ms <= read_timestamp_ms - state->g_last_thermal_temp_read_ms)) {
      state->g_last_thermal_temp_read_ms = read_timestamp_ms;
      if ((state->g_tz_cached & 1) == 0) {
        state->g_tz_count = 0;
        state->g_tz_cached = 1;
        memset(state->g_thermal_deny_source, 0, 1);
        directory = opendir("/sys/class/thermal");
        if (directory != (DIR *)0x0) {
          entry = readdir(directory);
          while ((entry != (dirent *)0x0 && (state->g_tz_count < 0xc))) {
            read_result = strncmp(entry->d_name,"thermal_zone",0xc);
            if ((read_result == 0) && (trigger_or_display = atoi(entry->d_name + 0xc), trigger_or_display < 0x50))
            {
              io_format(&io,scratch,0x100,"/sys/class/thermal/%s/type",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(entry->d_name)}},1);
              stream = fopen(scratch,"r");
              if (stream != (FILE *)0x0) {
                VIEW8(thermal_type + 8) = 0;
                VIEW8(thermal_type + 0) = 0;
                VIEW8(thermal_type + 24) = 0;
                VIEW8(thermal_type + 16) = 0;
                VIEW8(thermal_type + 40) = 0;
                VIEW8(thermal_type + 32) = 0;
                VIEW8(thermal_type + 56) = 0;
                VIEW8(thermal_type + 48) = 0;
                reason_text = fgets((char *)&VIEW8(thermal_type + 0),0x40,stream);
                if ((reason_text != (char *)0x0) &&
                   (reason_text = strchr((char *)&VIEW8(thermal_type + 0),10), reason_text != (char *)0x0)) {
                  *reason_text = '\0';
                }
                fclose(stream);
                reason_text = strstr((char *)&VIEW8(thermal_type + 0),"battery");
                if ((((reason_text == (char *)0x0) &&
                     (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"batt"),
                     reason_text == (char *)0x0)) &&
                    (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"skin"), reason_text == (char *)0x0
                    )) && (((reason_text = strstr((char *)&VIEW8(thermal_type + 0),"back"),
                            reason_text == (char *)0x0 &&
                            (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"charger"),
                            reason_text == (char *)0x0)) &&
                           (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"chg"),
                           reason_text == (char *)0x0)))) {
                  reason_text = strstr((char *)&VIEW8(thermal_type + 0),"display");
                  if (reason_text == (char *)0x0) {
                    reason_text = strstr((char *)&VIEW8(thermal_type + 0),"disp");
                    if (reason_text == (char *)0x0) {
                      reason_text = strstr((char *)&VIEW8(thermal_type + 0),"ibat");
                      if ((((reason_text != (char *)0x0) ||
                           (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"bcl"),
                           reason_text != (char *)0x0)) ||
                          ((reason_text = strstr((char *)&VIEW8(thermal_type + 0),"vbat"),
                           reason_text != (char *)0x0 ||
                           (reason_text = strstr((char *)&VIEW8(thermal_type + 0),"lvl"),
                           reason_text != (char *)0x0)))) &&
                         (state->g_thermal_deny_source[0] == '\0')) {
                        strncpy(state->g_thermal_deny_source,(char *)&VIEW8(thermal_type + 0),0x1f);
                      }
                      goto thermal_zone_scan_next;
                    }
                    type_prefix = 2;
                  }
                  else {
                    type_prefix = 2;
                  }
                }
                else {
                  type_prefix = 1;
                }
                read_result = state->g_tz_count;
                zone_offset = (int64_t)state->g_tz_count;
                zone_index = zone_offset * 4;
                state->g_tz_indices[zone_offset] = trigger_or_display;
                state->g_tz_classes[zone_offset] = type_prefix;
                strncpy(state->g_tz_names + zone_offset * 0x20,(char *)&VIEW8(thermal_type + 0),0x1f);
                state->g_tz_count = read_result + 1;
              }
            }
thermal_zone_scan_next:
            entry = readdir(directory);
          }
          closedir(directory);
        }
      }
      VIEW8(thermal_source + 8) = 0;
      VIEW8(thermal_source + 0) = 0;
      VIEW8(thermal_source + 24) = 0;
      VIEW8(thermal_source + 16) = 0;
      VIEW8(thermal_type + 8) = 0;
      VIEW8(thermal_type + 0) = 0;
      VIEW8(thermal_type + 24) = 0;
      VIEW8(thermal_type + 16) = 0;
      if (state->g_tz_count < 1) {
        state->g_temp_battery_mdeg = 0;
        state->g_temp_charger_mdeg = 0;
        state->g_temp_display_mdeg = 0;
        state->g_thermal_trusted_temp_mdeg = 0;
        trusted_temperature = 0;
        state->g_thermal_suspect_temp_mdeg = 0;
        selected_temperature = 0;
        trigger_or_display = state->cfg_thermal_hard_trigger;
        resume_or_charger = state->cfg_thermal_hard_resume;
        suspect_temperature = 0;
      }
      else {
        zone_index = 0;
        trigger_or_display = 0;
        resume_or_charger = 0;
        selected_temperature = 0;
        reason_text = state->g_tz_names;
        suspect_temperature = 0;
        trusted_temperature = 0;
        do {
          io_format(&io,scratch,0x100,"/sys/class/thermal/thermal_zone%d/temp",(IoArgument[]){{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)state->g_tz_indices[zone_index])}},1);
          stream = fopen(scratch,"r");
          if (stream != (FILE *)0x0) {
            temperature_scratch = 0;
            read_result = fscanf(stream,"%d",&temperature_scratch);
            if (read_result != 1) {
              temperature_scratch = 0;
            }
            fclose(stream);
            sample_or_floor = temperature_scratch / 1000;
            if ((int)temperature_scratch < 0x30d41) {
              sample_or_floor = temperature_scratch;
            }
            text_cursor = strstr(reason_text,"battery");
            if (((text_cursor != (char *)0x0) ||
                (text_cursor = strstr(reason_text,"batt"), battery_temperature = selected_temperature,
                text_cursor != (char *)0x0)) &&
               (battery_temperature = sample_or_floor, (int)sample_or_floor <= (int)selected_temperature)) {
              battery_temperature = selected_temperature;
            }
            text_cursor = strstr(reason_text,"charger");
            if (((text_cursor != (char *)0x0) ||
                (text_cursor = strstr(reason_text,"chg"), selected_temperature = resume_or_charger,
                text_cursor != (char *)0x0)) &&
               (selected_temperature = sample_or_floor, (int)sample_or_floor <= (int)resume_or_charger)) {
              selected_temperature = resume_or_charger;
            }
            text_cursor = strstr(reason_text,"display");
            if (((text_cursor != (char *)0x0) ||
                (text_cursor = strstr(reason_text,"disp"), resume_or_charger = trigger_or_display,
                text_cursor != (char *)0x0)) &&
               (resume_or_charger = sample_or_floor, (int)sample_or_floor <= (int)trigger_or_display)) {
              resume_or_charger = trigger_or_display;
            }
            trigger_or_display = resume_or_charger;
            resume_or_charger = selected_temperature;
            selected_temperature = battery_temperature;
            if (state->g_tz_classes[zone_index] == 2) {
              if ((int)suspect_temperature < (int)sample_or_floor) {
                text_cursor = (char *)&VIEW8(thermal_source + 0);
                suspect_temperature = sample_or_floor;
                goto thermal_source_selected;
              }
            }
            else if ((state->g_tz_classes[zone_index] == 1) &&
                    ((int)trusted_temperature < (int)sample_or_floor)) {
              text_cursor = (char *)&VIEW8(thermal_type + 0);
              trusted_temperature = sample_or_floor;
thermal_source_selected:
              strncpy(text_cursor,reason_text,0x1f);
            }
          }
          zone_index = zone_index + 1;
          reason_text = reason_text + 0x20;
        } while (zone_index < state->g_tz_count);
        state->g_thermal_trusted_temp_mdeg = trusted_temperature;
        state->g_thermal_suspect_temp_mdeg = suspect_temperature;
        state->g_temp_battery_mdeg = selected_temperature;
        state->g_temp_charger_mdeg = resume_or_charger;
        state->g_temp_display_mdeg = trigger_or_display;
        if ((int)trusted_temperature < 1) {
          if ((int)suspect_temperature < 1) {
            selected_temperature = 0;
            trigger_or_display = state->cfg_thermal_hard_trigger;
            resume_or_charger = state->cfg_thermal_hard_resume;
          }
          else {
            state->g_tz_last_class = 2;
            strncpy(state->g_tz_last_source,(char *)&VIEW8(thermal_source + 0),0x1f);
            strncpy(state->g_thermal_suspect_source,(char *)&VIEW8(thermal_source + 0),0x1f);
            selected_temperature = suspect_temperature;
            trigger_or_display = state->cfg_thermal_hard_trigger;
            resume_or_charger = state->cfg_thermal_hard_resume;
          }
        }
        else {
          state->g_tz_last_class = 1;
          strncpy(state->g_tz_last_source,(char *)&VIEW8(thermal_type + 0),0x1f);
          strncpy(state->g_thermal_trusted_source,(char *)&VIEW8(thermal_type + 0),0x1f);
          selected_temperature = trusted_temperature;
          trigger_or_display = state->cfg_thermal_hard_trigger;
          resume_or_charger = state->cfg_thermal_hard_resume;
        }
      }
    }
    state->cfg_thermal_hard_trigger = trigger_or_display;
    state->cfg_thermal_hard_resume = resume_or_charger;
    state->g_thermal_temp = selected_temperature;
    if (selected_temperature == 0) {
      if (state->g_thermal_deny_source[0] != '\0') {
        state->g_thermal_source_filtered = 1;
        io_format(&io,state->g_thermal_guard_source,0x20,"%s",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(state->g_thermal_deny_source)}},1);
      }
      if (state->g_heat_guard_active == 1) {
        limited_br = state->g_thermal_floor_latched_br;
        if (state->g_thermal_floor_latched_br < 1) {
          limited_br = (uint32_t)(fmaf(state->cfg_thermal_hard_floor_pct, (float)state->g_max, 0.5f));
        }
        if ((int)limited_br < 2) {
          limited_br = 1;
        }
        state->g_thermal_cap_br = limited_br;
        if ((int)requested_br <= (int)limited_br) {
          limited_br = requested_br;
          state->g_thermal_cap_br = requested_br;
        }
      }
    }
    else {
      clock_gettime(1,(timespec *)scratch);
      refresh_battery_state((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000);
      if ((state->cfg_charging_heat_guard != 0) && (state->g_cached_charging != 0)) {
        if ((int)trigger_or_display < 0xcb21) {
          trigger_or_display = 52000;
        }
        if ((int)resume_or_charger < 0xb799) {
          resume_or_charger = 47000;
        }
        trigger_or_display = trigger_or_display - 2000;
        resume_or_charger = resume_or_charger - 2000;
      }
      sample_or_floor = (uint32_t)(fmaf(state->cfg_thermal_hard_floor_pct, (float)state->g_max, 0.5f));
      if ((int)sample_or_floor < 2) {
        sample_or_floor = 1;
      }
      reason_text = "-";
      if (state->g_tz_last_source[0] != '\0') {
        reason_text = state->g_tz_last_source;
      }
      io_format(&io,state->g_thermal_guard_source,0x20,"%s",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(reason_text)}},1);
      read_timestamp_ms = state->g_thermal_exit_settle_until;
                      if ((state->g_heat_guard_active & 1) == 0) {
        if ((int)trigger_or_display <= (int)trusted_temperature) {
          state->g_heat_guard_active = 1;
          state->g_thermal_entry = 1;
          state->g_thermal_floor_latched_br = sample_or_floor;
          clock_gettime(1,(timespec *)scratch);
          state->g_thermal_exit_settle_until = 0;
          state->g_thermal_guard_confirm_cnt = 0;
          state->g_thermal_emergency_bypass =
               (int)(64999 < (int)trusted_temperature || 51999 < state->g_temp_battery_mdeg);
          io_format(&io,state->g_thermal_guard_source,0x20,"%s",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(state->g_thermal_trusted_source)}},1);
          stream = (FILE *)domain_stdout();
thermal_entry_log:
          io_print(&io,stream,"[%s] thermal_guard: ENTER temp=%d trigger=%d resume=%d floor=%d source=%s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)trusted_temperature)},{.word=(uint64_t)(uintptr_t)((uint64_t)trigger_or_display)},{.word=(uint64_t)(uintptr_t)((uint64_t)resume_or_charger)},{.word=(uint64_t)(uintptr_t)((uint64_t)sample_or_floor)},{.word=(uint64_t)(uintptr_t)(state->g_thermal_guard_source)}},6);
          suspect_temperature = state->g_thermal_floor_latched_br;
          goto thermal_floor_apply;
        }
        if (((int)suspect_temperature < (int)trigger_or_display) ||
           (((state->g_temp_battery_mdeg < 46000 && (state->g_temp_charger_mdeg < 46000)) &&
            (state->g_temp_display_mdeg < 46000)))) {
          state->g_thermal_guard_confirm_cnt = 0;
          clock_gettime(1,(timespec *)scratch);
          settle_until_ms = state->g_thermal_exit_settle_until;
          if ((((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000 < read_timestamp_ms) &&
              (0 < state->g_thermal_floor_latched_br)) &&
             (state->g_thermal_floor_latched_br < (int)requested_br)) {
            clock_gettime(1,(timespec *)scratch);
            settle_fraction = (float)((settle_until_ms + (int64_t)VIEW8(scratch) * -1000) - VIEW8(scratch + 8) / 1000000) /
                     -8000.0f + 1.0f;
            upper_fraction = 1.0f;
            if (settle_fraction <= 1.0f) {
              upper_fraction = settle_fraction;
            }
            bounded_fraction = 0.0f;
            if (0.0f <= settle_fraction) {
              bounded_fraction = upper_fraction;
            }
            limited_br = state->g_thermal_floor_latched_br +
                    (int)(fmaf(bounded_fraction, (float)(int)(requested_br - state->g_thermal_floor_latched_br), 0.5f));
            state->g_thermal_cap_br = limited_br;
          }
        }
        else if ((999999 < state->g_thermal_guard_confirm_cnt) ||
                (read_result = state->g_thermal_guard_confirm_cnt + 1,
                confirmed = 1 < state->g_thermal_guard_confirm_cnt,
                state->g_thermal_guard_confirm_cnt = read_result, confirmed)) {
          state->g_heat_guard_active = 1;
          state->g_thermal_entry = 1;
          state->g_thermal_floor_latched_br = sample_or_floor;
          clock_gettime(1,(timespec *)scratch);
          state->g_thermal_exit_settle_until = 0;
          state->g_thermal_emergency_bypass = 0;
          io_format(&io,state->g_thermal_guard_source,0x20,"%s",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(state->g_thermal_suspect_source)}},1);
          stream = (FILE *)domain_stdout();
          trusted_temperature = suspect_temperature;
          goto thermal_entry_log;
        }
      }
      else {
        if ((int)suspect_temperature < 1) {
          suspect_temperature = selected_temperature;
        }
        if ((int)trusted_temperature < 1) {
          trusted_temperature = suspect_temperature;
        }
        suspect_temperature = sample_or_floor;
        if ((int)resume_or_charger < (int)trusted_temperature) {
thermal_floor_apply:
          state->g_thermal_floor_latched_br = suspect_temperature;
          limited_br = sample_or_floor;
          state->g_thermal_cap_br = sample_or_floor;
          if ((int)requested_br <= (int)sample_or_floor) {
            limited_br = requested_br;
            state->g_thermal_cap_br = requested_br;
          }
        }
        else {
          state->g_heat_guard_active = 0;
          clock_gettime(1,(timespec *)scratch);
          state->g_thermal_exit_settle_until =
               (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000 + 8000;
          state->g_thermal_guard_confirm_cnt = 0;
          io_print(&io,(FILE *)domain_stdout(),"[%s] thermal_guard: EXIT temp=%d trigger=%d resume=%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)trusted_temperature)},{.word=(uint64_t)(uintptr_t)((uint64_t)trigger_or_display)},{.word=(uint64_t)(uintptr_t)((uint64_t)resume_or_charger)}},4);
        }
      }
    }
  }
  return (int32_t)limited_br;
}

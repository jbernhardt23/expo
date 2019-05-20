package host.exp.exponent.notifications.managers;

import android.content.Context;
import com.raizlabs.android.dbflow.sql.language.Select;
import org.unimodules.core.interfaces.Function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import host.exp.exponent.notifications.exceptions.UnableToScheduleException;
import host.exp.exponent.notifications.interfaces.SchedulerInterface;
import host.exp.exponent.notifications.interfaces.SchedulersManagerInterface;
import host.exp.exponent.notifications.schedulers.CalendarScheduler;
import host.exp.exponent.notifications.schedulers.IntervalScheduler;

class SchedulerManager implements SchedulersManagerInterface {

  private boolean mFetchedFromDB = false;

  private HashMap<String, SchedulerInterface> mSchedulersMap = new HashMap<>();

  private Context mApplicationContext;

  SchedulerManager(Context applicationContext) {
    mApplicationContext = applicationContext;
  }

  @Override
  public void triggerAll(String action) {
    fetchSchedulersMap();

    cancelAlreadyScheduled(null);

    ArrayList<String> unsuccessful = new ArrayList<String>();

    for (Map.Entry<String, SchedulerInterface> scheduler : mSchedulersMap.entrySet()) {
      try {
        scheduler.getValue().schedule(action);
      } catch (UnableToScheduleException e) {
        unsuccessful.add(scheduler.getKey());
      }
    }

    for (String key : unsuccessful) {
      this.removeScheduler(key);
    }
  }

  @Override
  public void removeAll(String experienceId) {
    fetchSchedulersMap();
    cancelAlreadyScheduled(experienceId);

    ArrayList<String> toRemove = new ArrayList<String>();

    for (Map.Entry<String, SchedulerInterface> scheduler : mSchedulersMap.entrySet()) {
      if (experienceId == null || scheduler.getValue().getOwnerExperienceId().equals(experienceId)) {
        scheduler.getValue().remove();
        toRemove.add(scheduler.getKey());
      }
    }

    for (String key : toRemove) {
      mSchedulersMap.remove(key);
    }
  }

  @Override
  public void cancelAlreadyScheduled(String experienceId) {
    fetchSchedulersMap();
    for (SchedulerInterface scheduler : mSchedulersMap.values()) {
      if (experienceId == null || scheduler.getOwnerExperienceId().equals(experienceId)) {
        scheduler.cancel();
      }
    }
  }

  @Override
  public void rescheduleOrDelete(String id) {
    fetchSchedulersMap();
    SchedulerInterface scheduler = mSchedulersMap.get(id);
    if (scheduler == null) {
      return;
    }

    scheduler.onPostSchedule();
    if (!scheduler.canBeRescheduled()) {
      this.removeScheduler(id);
    } else {
      try {
        scheduler.schedule(null);
      } catch (UnableToScheduleException e) {
        this.removeScheduler(id);
      }
    }
  }

  @Override
  public void removeScheduler(String id) {
    fetchSchedulersMap();
    SchedulerInterface scheduler = mSchedulersMap.get(id);
    if (scheduler == null) {
      return;
    }
    mSchedulersMap.remove(id);
    scheduler.cancel();
    scheduler.remove();
  }

  @Override
  public void addScheduler(SchedulerInterface scheduler, Function<String, Boolean> handler) {
    fetchSchedulersMap();

    scheduler.setApplicationContext(mApplicationContext);
    String id = scheduler.saveAndGetId();
    mSchedulersMap.put(id, scheduler);
    try {
      scheduler.schedule(null);
    } catch (UnableToScheduleException e) {
      this.removeScheduler(id);
      id = null;
    }
    handler.apply(id);
  }

  private List<Class> getSchedulerClasses() {
    return Arrays.asList(CalendarScheduler.class, IntervalScheduler.class);
  }

  private void fetchSchedulersMap() {
    if (!mFetchedFromDB) {
      mFetchedFromDB = true;

      for (Class schedulerClass : getSchedulerClasses()) {
        List<SchedulerInterface> schedulers = new Select().from(schedulerClass).queryList();
        for (SchedulerInterface scheduler : schedulers) {
          mSchedulersMap.put(scheduler.getIdAsString(), scheduler);
        }
      }

      for (SchedulerInterface scheduler : mSchedulersMap.values()) {
        scheduler.setApplicationContext(mApplicationContext);
      }
    }
  }

}

package group5.example.sportCenter.repository;

import java.util.List;

import group5.example.sportCenter.entity.Notification;

public interface NotificationRepositoryCustom {

    int insertBroadcastNotifications(List<Notification> notifications);
}

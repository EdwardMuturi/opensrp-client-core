package org.smartregister.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.gson.reflect.TypeToken;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.clientandeventmodel.DateUtil;
import org.smartregister.domain.db.Client;
import org.smartregister.domain.db.Column;
import org.smartregister.domain.db.ColumnAttribute;
import org.smartregister.domain.db.Event;
import org.smartregister.domain.db.EventClient;
import org.smartregister.util.JsonFormUtils;
import org.smartregister.util.Utils;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by keyman on 27/07/2017.
 */
public class EventClientRepository extends BaseRepository {
    private static final String TAG = BaseRepository.class.getCanonicalName();

    private static final String EVENT_ID = "id";

    public EventClientRepository(Repository repository) {
        super(repository);
    }


    private void populateAdditionalColumns(ContentValues values, Column[] columns, JSONObject jsonObject) {
        for (Column column : columns) {
            try {
                if (values.containsKey(column.name()))//column already added
                    continue;
                if (jsonObject.has(column.name())) {
                    Object value = jsonObject.get(column.name());
                    if (column.column().type().equals(ColumnAttribute.Type.date)) {
                        values.put(column.name(), dateFormat.format(new DateTime(value).toDate()));
                    } else if (column.column().type().equals(ColumnAttribute.Type.longnum)) {
                        values.put(column.name(), Long.valueOf(value.toString()));
                    } else {
                        values.put(column.name(), value.toString());
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error extracting " + column.name(), e);
            }
        }

    }

    public Boolean checkIfExists(Table table, String baseEntityId) {
        Cursor mCursor = null;
        try {
            String query = "SELECT "
                    + event_column.baseEntityId
                    + " FROM "
                    + table.name()
                    + " WHERE "
                    + event_column.baseEntityId
                    + " = ?";
            mCursor = getWritableDatabase().rawQuery(query, new String[]{baseEntityId});
            if (mCursor != null && mCursor.moveToFirst()) {

                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        } finally {
            if (mCursor != null) {
                mCursor.close();
            }
        }
        return false;
    }

    public Boolean checkIfExistsByFormSubmissionId(Table table, String formSubmissionId) {
        Cursor mCursor = null;
        try {
            String query = "SELECT "
                    + event_column.formSubmissionId
                    + " FROM "
                    + table.name()
                    + " WHERE "
                    + event_column.formSubmissionId
                    + " =?";
            mCursor = getWritableDatabase().rawQuery(query, new String[]{formSubmissionId});
            if (mCursor != null && mCursor.moveToFirst()) {

                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        } finally {
            if (mCursor != null) {
                mCursor.close();
            }
        }
        return false;
    }

    private boolean populateStatement(SQLiteStatement statement, Table table, JSONObject jsonObject, Map<String, Integer> columnOrder) {
        if (statement == null)
            return false;
        statement.clearBindings();
        List columns;
        try {
            if (table.equals(Table.client)) {
                columns = Arrays.asList(client_column.values());
                statement.bindString(columnOrder.get(client_column.json.name()), jsonObject.toString());
                statement.bindString(columnOrder.get(client_column.updatedAt.name()), dateFormat.format(new Date()));
                statement.bindString(columnOrder.get(client_column.syncStatus.name()), BaseRepository.TYPE_Synced);
                statement.bindString(columnOrder.get(client_column.validationStatus.name()), BaseRepository.TYPE_Valid);
                statement.bindString(columnOrder.get(client_column.baseEntityId.name()), jsonObject.getString(client_column.baseEntityId.name()));
            } else if (table.equals(Table.event)) {
                columns = Arrays.asList(event_column.values());
                statement.bindString(columnOrder.get(event_column.json.name()), jsonObject.toString());
                statement.bindString(columnOrder.get(event_column.updatedAt.name()), dateFormat.format(new Date()));
                statement.bindString(columnOrder.get(event_column.syncStatus.name()), BaseRepository.TYPE_Synced);
                statement.bindString(columnOrder.get(event_column.validationStatus.name()), BaseRepository.TYPE_Valid);
                statement.bindString(columnOrder.get(event_column.baseEntityId.name()), jsonObject.getString(event_column.baseEntityId.name()));
                if (jsonObject.has(EVENT_ID))
                    statement.bindString(columnOrder.get(event_column.eventId.name()), jsonObject.getString(EVENT_ID));
            } else {
                return false;
            }

            List<? extends Column> otherColumns = new ArrayList(columns);
            if (!otherColumns.isEmpty()) {
                otherColumns.removeAll(Arrays.asList(client_column.json, client_column.updatedAt, client_column.syncStatus, client_column.validationStatus, client_column.baseEntityId,
                        event_column.json, event_column.updatedAt, event_column.syncStatus, event_column.validationStatus, event_column.baseEntityId, event_column.eventId));
            }

            for (Column column : otherColumns) {
                if (jsonObject.has(column.name())) {
                    Object value = jsonObject.get(column.name());
                    if (column.column().type().equals(ColumnAttribute.Type.date)) {
                        statement.bindString(columnOrder.get(column.name()), dateFormat.format(new DateTime(value).toDate()));
                    } else if (column.column().type().equals(ColumnAttribute.Type.longnum)) {
                        statement.bindLong(columnOrder.get(column.name()), Long.valueOf(value.toString()));
                    } else {
                        statement.bindString(columnOrder.get(column.name()), value.toString());
                    }
                } else {
                    statement.bindNull(columnOrder.get(column.name()));
                }
            }
            return true;
        } catch (JSONException e) {
            Log.e(getClass().getName(), e.getMessage(), e);
            return false;
        }
    }


    private QueryWrapper generateInsertQuery(Table table) {

        QueryWrapper queryWrapper = new QueryWrapper();
        Map<String, Integer> columnOrder = new HashMap();

        StringBuilder queryBuilder = new StringBuilder("INSERT  INTO ");
        queryBuilder.append(table.name());
        queryBuilder.append(" (");
        StringBuilder params = new StringBuilder(" VALUES( ");

        for (int i = 0; i < table.columns().length; i++) {

            queryBuilder.append(table.columns()[i].name() + ",");
            params.append("?,");
            columnOrder.put(table.columns()[i].name(), i + 1);
        }

        queryBuilder.setLength(queryBuilder.length() - 1);
        params.setLength(params.length() - 1);
        queryBuilder.append(")");
        queryBuilder.append(params);
        queryBuilder.append(")");

        queryWrapper.sqlQuery = queryBuilder.toString();
        queryWrapper.columnOrder = columnOrder;

        return queryWrapper;
    }

    private QueryWrapper generateUpdateQuery(Table table) {

        QueryWrapper queryWrapper = new QueryWrapper();
        Map<String, Integer> columnOrder = new HashMap();

        Column filterColumn;

        if (table.equals(Table.client))
            filterColumn = client_column.baseEntityId;
        else if (table.equals(Table.event))
            filterColumn = event_column.formSubmissionId;
        else return null;
        StringBuilder queryBuilder = new StringBuilder("UPDATE ");
        queryBuilder.append(table.name());
        queryBuilder.append(" SET ");

        for (int i = 0; i < table.columns().length; i++) {
            if (table.columns()[i].equals(filterColumn))
                continue;
            queryBuilder.append(table.columns()[i].name() + "=?,");
            columnOrder.put(table.columns()[i].name(), columnOrder.size() + 1);
        }

        queryBuilder.setLength(queryBuilder.length() - 1);
        queryBuilder.append(" WHERE ");
        queryBuilder.append(filterColumn.name() + "=?");
        columnOrder.put(filterColumn.name(), columnOrder.size() + 1);

        queryWrapper.sqlQuery = queryBuilder.toString();
        queryWrapper.columnOrder = columnOrder;

        return queryWrapper;
    }

    public boolean batchInsertClients(JSONArray array) {
        if (array == null || array.length() == 0) {
            return false;
        }
        SQLiteStatement insertStatement = null;
        SQLiteStatement updateStatement = null;
        try {
            getWritableDatabase().beginTransaction();

            QueryWrapper insertQueryWrapper = generateInsertQuery(Table.client);

            QueryWrapper updateQueryWrapper = generateUpdateQuery(Table.client);

            insertStatement = getWritableDatabase().compileStatement(insertQueryWrapper.sqlQuery);

            updateStatement = getWritableDatabase().compileStatement(updateQueryWrapper.sqlQuery);

            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject jsonObject = array.getJSONObject(i);
                    String baseEntityId = jsonObject.getString(client_column.baseEntityId.name());
                    if (checkIfExists(Table.client, baseEntityId)) {
                        if (populateStatement(updateStatement, Table.client, jsonObject, updateQueryWrapper.columnOrder))
                            updateStatement.executeUpdateDelete();
                        else
                            Log.w(TAG, "Unable to update client with baseEntityId: " + baseEntityId);

                    } else {
                        if (populateStatement(insertStatement, Table.client, jsonObject, insertQueryWrapper.columnOrder))
                            insertStatement.executeInsert();
                        else
                            Log.w(TAG, "Unable to add client with baseEntityId: " + baseEntityId);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException", e);
                }
            }
            getWritableDatabase().setTransactionSuccessful();
            getWritableDatabase().endTransaction();
            return true;
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
            return false;
        } finally {
            if (insertStatement != null)
                insertStatement.close();
            if (updateStatement != null)
                updateStatement.close();
        }
    }

    public boolean batchInsertEvents(JSONArray array, long serverVersion) {
        if (array == null || array.length() == 0) {
            return false;
        }

        SQLiteStatement insertStatement = null;
        SQLiteStatement updateStatement = null;

        try {

            getWritableDatabase().beginTransaction();

            QueryWrapper insertQueryWrapper = generateInsertQuery(Table.event);

            QueryWrapper updateQueryWrapper = generateUpdateQuery(Table.event);

            insertStatement = getWritableDatabase().compileStatement(insertQueryWrapper.sqlQuery);

            updateStatement = getWritableDatabase().compileStatement(updateQueryWrapper.sqlQuery);
            for (int i = 0; i < array.length(); i++) {
                JSONObject jsonObject = array.getJSONObject(i);
                String formSubmissionId = jsonObject.getString(event_column.formSubmissionId.name());
                if (checkIfExistsByFormSubmissionId(Table.event, formSubmissionId)) {
                    if (populateStatement(updateStatement, Table.event, jsonObject, updateQueryWrapper.columnOrder))
                        updateStatement.executeUpdateDelete();
                    else
                        Log.w(TAG, "Unable to update event with formSubmissionId:  " + formSubmissionId);
                } else {
                    if (populateStatement(insertStatement, Table.event, jsonObject, insertQueryWrapper.columnOrder))
                        insertStatement.executeInsert();
                    else
                        Log.w(TAG, "Unable to update event with formSubmissionId: " + formSubmissionId);
                }
            }
            getWritableDatabase().setTransactionSuccessful();
            getWritableDatabase().endTransaction();
            return true;
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
            return false;
        } finally {
            if (insertStatement != null)
                insertStatement.close();
            if (updateStatement != null)
                updateStatement.close();
        }
    }

    public <T> T convert(JSONObject jo, Class<T> t) {
        if (jo == null) {
            return null;
        }
        return convert(jo.toString(), t);
    }

    public <T> T convert(String jsonString, Class<T> t) {
        if (StringUtils.isBlank(jsonString)) {
            return null;
        }
        try {
            return JsonFormUtils.gson.fromJson(jsonString, t);
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
            Log.e(getClass().getName(), "Unable to convert: " + jsonString);
            return null;
        }
    }

    public JSONObject convertToJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return new JSONObject(JsonFormUtils.gson.toJson(object));
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
            Log.e(getClass().getName(), "Unable to convert to json : " + object.toString());
            return null;
        }
    }

    public Pair<Long, Long> getMinMaxServerVersions(JSONObject jsonObject) {
        final String EVENTS = "events";
        try {
            if (jsonObject != null && jsonObject.has(EVENTS)) {
                JSONArray events = jsonObject.getJSONArray(EVENTS);
                Type listType = new TypeToken<List<Event>>() {
                }.getType();
                List<Event> eventList = JsonFormUtils.gson.fromJson(events.toString(), listType);

                long maxServerVersion = Long.MIN_VALUE;
                long minServerVersion = Long.MAX_VALUE;

                for (Event event : eventList) {
                    long serverVersion = event.getServerVersion();
                    if (serverVersion > maxServerVersion) {
                        maxServerVersion = serverVersion;
                    }

                    if (serverVersion < minServerVersion) {
                        minServerVersion = serverVersion;
                    }
                }
                return Pair.create(minServerVersion, maxServerVersion);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), e.getMessage(), e);
        }
        return Pair.create(0L, 0L);
    }

    public List<JSONObject> getEvents(long startServerVersion, long lastServerVersion) {
        List<JSONObject> list = new ArrayList<JSONObject>();
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT json FROM "
                            + Table.event.name()
                            + " WHERE "
                            + event_column.serverVersion.name()
                            + " > "
                            + startServerVersion
                            + " AND "
                            + event_column.serverVersion.name()
                            + " <= "
                            + lastServerVersion
                            + " ORDER BY "
                            + event_column.serverVersion.name(),
                    null);
            while (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                JSONObject ev = new JSONObject(jsonEventStr);

                if (ev.has(event_column.baseEntityId.name())) {
                    String baseEntityId = ev.getString(event_column.baseEntityId.name());
                    JSONObject cl = getClient(getWritableDatabase(), baseEntityId);
                    ev.put("client", cl);
                }
                list.add(ev);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public List<EventClient> fetchEventClients(long startServerVersion, long lastServerVersion) {
        List<EventClient> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT json FROM "
                            + Table.event.name()
                            + " WHERE "
                            + event_column.serverVersion.name()
                            + " > ? AND "
                            + event_column.serverVersion.name()
                            + " <= ?  ORDER BY "
                            + event_column.serverVersion.name(),
                    new String[]{String.valueOf(startServerVersion), String.valueOf(lastServerVersion)});
            while (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);
                if (StringUtils.isBlank(jsonEventStr)
                        || "{}".equals(jsonEventStr)) { // Skip blank/empty json string
                    continue;
                }
                jsonEventStr = jsonEventStr.replaceAll("'", "");

                Event event = convert(jsonEventStr, Event.class);

                String baseEntityId = event.getBaseEntityId();
                Client client = fetchClientByBaseEntityId(baseEntityId);

                EventClient eventClient = new EventClient(event, client);
                list.add(eventClient);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * Get a list of events and client for a list of event types
     *
     * @param eventTypes the list of event types
     * @return a list of events and clients
     */
    public List<EventClient> fetchEventClientsByEventTypes(List<String> eventTypes) {
        if (eventTypes == null)
            return null;
        List<EventClient> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            String eventTypeString = TextUtils.join(",", Collections.nCopies(eventTypes.size(), "?"));

            cursor = getWritableDatabase().rawQuery(String.format("SELECT json FROM "
                            + Table.event.name()
                            + " WHERE " + event_column.eventType.name() + " IN (%s)  "
                            + " ORDER BY " + event_column.serverVersion.name(), eventTypeString),
                    eventTypes.toArray(new String[]{}));
            while (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);
                if (StringUtils.isBlank(jsonEventStr)
                        || "{}".equals(jsonEventStr)) { // Skip blank/empty json string
                    continue;
                }
                jsonEventStr = jsonEventStr.replaceAll("'", "");

                Event event = convert(jsonEventStr, Event.class);

                String baseEntityId = event.getBaseEntityId();
                Client client = fetchClientByBaseEntityId(baseEntityId);

                EventClient eventClient = new EventClient(event, client);
                list.add(eventClient);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public List<JSONObject> getEvents(Date lastSyncDate) {

        String lastSyncString = DateUtil.yyyyMMddHHmmss.format(lastSyncDate);

        List<JSONObject> eventAndAlerts = new ArrayList<JSONObject>();

        String query = "select "
                + event_column.json
                + ","
                + event_column.updatedAt
                + " from "
                + Table.event.name()
                + " where "
                + event_column.updatedAt
                + " > ? and length("
                + event_column.json
                + ")>2 order by "
                + event_column.serverVersion
                + " asc ";
        Cursor cursor = getWritableDatabase().rawQuery(query, new String[]{lastSyncString});

        try {
            while (cursor.moveToNext()) {
                String jsonEventStr = (cursor.getString(0));
                // String jsonEventStr = new String(json, "UTF-8");
                if (StringUtils.isBlank(jsonEventStr)
                        || "{}".equals(jsonEventStr)) { // Skip blank/empty json string
                    continue;
                }

                JSONObject jsonObectEventOrAlert = new JSONObject(jsonEventStr);
                String type =
                        jsonObectEventOrAlert.has("type") ? jsonObectEventOrAlert.getString("type")
                                : null;
                if (StringUtils.isBlank(type)) { // Skip blank types
                    continue;
                }

                if (!"Event".equals(type)
                        && !"Action".equals(type)) { // Skip type that isn't Event or Action
                    continue;
                }
                if (jsonObectEventOrAlert.has(event_column.baseEntityId.name())) {
                    String baseEntityId = jsonObectEventOrAlert.getString(event_column
                            .baseEntityId
                            .name());
                    JSONObject cl = getClientByBaseEntityId(baseEntityId);
                    jsonObectEventOrAlert.put("client", cl);
                }

                eventAndAlerts.add(jsonObectEventOrAlert);
                try {
                    lastSyncDate.setTime(DateUtil.yyyyMMddHHmmss.parse(cursor.getString(1))
                            .getTime());
                } catch (ParseException e) {
                    Log.e(TAG, e.toString(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            cursor.close();
        }

        return eventAndAlerts;
    }

    public List<JSONObject> getEvents(Date lastSyncDate, String syncStatus) {

        String lastSyncString = DateUtil.yyyyMMddHHmmss.format(lastSyncDate);

        List<JSONObject> eventAndAlerts = new ArrayList<>();

        String query = "select "
                + event_column.json
                + ","
                + event_column.updatedAt
                + " from "
                + Table.event.name()
                + " where "
                + event_column.syncStatus
                + " = ? and "
                + event_column.updatedAt
                + " > ? and length("
                + event_column.json
                + ")>2 order by "
                + event_column.serverVersion
                + " asc ";
        Cursor cursor = getWritableDatabase().rawQuery(query, new String[]{syncStatus, lastSyncString});

        try {
            while (cursor.moveToNext()) {
                String jsonEventStr = (cursor.getString(0));
                // String jsonEventStr = new String(json, "UTF-8");
                if (StringUtils.isBlank(jsonEventStr)
                        || "{}".equals(jsonEventStr)) { // Skip blank/empty json string
                    continue;
                }

                JSONObject jsonObectEventOrAlert = new JSONObject(jsonEventStr);
                String type =
                        jsonObectEventOrAlert.has("type") ? jsonObectEventOrAlert.getString("type")
                                : null;
                if (StringUtils.isBlank(type)) { // Skip blank types
                    continue;
                }

                if (!"Event".equals(type)
                        && !"Action".equals(type)) { // Skip type that isn't Event or Action
                    continue;
                }
                if (jsonObectEventOrAlert.has(event_column.baseEntityId.name())) {
                    String baseEntityId = jsonObectEventOrAlert.getString(event_column
                            .baseEntityId
                            .name());
                    JSONObject cl = getClientByBaseEntityId(baseEntityId);
                    jsonObectEventOrAlert.put("client", cl);
                }

                eventAndAlerts.add(jsonObectEventOrAlert);
                try {
                    lastSyncDate.setTime(DateUtil.yyyyMMddHHmmss.parse(cursor.getString(1))
                            .getTime());
                } catch (ParseException e) {
                    Log.e(TAG, e.toString(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            cursor.close();
        }

        return eventAndAlerts;
    }

    public List<EventClient> fetchEventClients(Date lastSyncDate, String syncStatus) {

        List<EventClient> list = new ArrayList<>();
        String lastSyncString = DateUtil.yyyyMMddHHmmss.format(lastSyncDate);

        String query = "select "
                + event_column.json
                + ","
                + event_column.updatedAt
                + " from "
                + Table.event.name()
                + " where "
                + event_column.syncStatus
                + " = ? and "
                + event_column.updatedAt
                + " > ? ORDER BY "
                + event_column.serverVersion.name();

        Cursor cursor = getWritableDatabase().rawQuery(query, new String[]{syncStatus, lastSyncString});

        try {
            while (cursor.moveToNext()) {
                String jsonEventStr = (cursor.getString(0));
                // String jsonEventStr = new String(json, "UTF-8");
                if (StringUtils.isBlank(jsonEventStr)
                        || "{}".equals(jsonEventStr)) { // Skip blank/empty json string
                    continue;
                }

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                Event event = convert(jsonEventStr, Event.class);

                String baseEntityId = event.getBaseEntityId();
                Client client = fetchClientByBaseEntityId(baseEntityId);

                EventClient eventClient = new EventClient(event, client);
                list.add(eventClient);

            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            cursor.close();
        }
        return list;
    }

    public Map<String, Object> getUnSyncedEvents(int limit) {
        Map<String, Object> result = new HashMap<>();
        List<JSONObject> clients = new ArrayList<JSONObject>();
        List<JSONObject> events = new ArrayList<JSONObject>();

        String query = "select "
                + event_column.json
                + ","
                + event_column.syncStatus
                + " from "
                + Table.event.name()
                + " where "
                + event_column.syncStatus
                + " = ?  and length("
                + event_column.json
                + ")>2 order by "
                + event_column.updatedAt
                + " asc limit "
                + limit;
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery(query, new String[]{BaseRepository.TYPE_Unsynced});

            while (cursor.moveToNext()) {
                String jsonEventStr = (cursor.getString(0));
                if (StringUtils.isBlank(jsonEventStr)
                        || jsonEventStr.equals("{}")) { // Skip blank/empty json string
                    continue;
                }
                jsonEventStr = jsonEventStr.replaceAll("'", "");
                JSONObject jsonObectEvent = new JSONObject(jsonEventStr);
                events.add(jsonObectEvent);
                if (jsonObectEvent.has(event_column.baseEntityId.name())) {
                    String baseEntityId = jsonObectEvent.getString(event_column.baseEntityId.name
                            ());
                    JSONObject cl = getUnSyncedClientByBaseEntityId(baseEntityId);
                    if (cl != null) {
                        clients.add(cl);
                    }
                }

            }
            if (!clients.isEmpty()) {
                result.put("clients", clients);
            }
            if (!events.isEmpty()) {
                result.put("events", events);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }


    public List<String> getUnValidatedEventFormSubmissionIds(int limit) {
        List<String> ids = new ArrayList<String>();

        final String validateFilter = " where "
                + event_column.syncStatus + " = ? "
                + " AND ( " + event_column.validationStatus + " is NULL or "
                + event_column.validationStatus + " != ? ) ";

        String query = "select "
                + event_column.formSubmissionId
                + " from "
                + Table.event.name()
                + validateFilter
                + ORDER_BY
                + event_column.updatedAt
                + " asc limit "
                + limit;

        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery(query, new String[]{BaseRepository.TYPE_Synced, BaseRepository.TYPE_Valid});
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String id = cursor.getString(0);
                    ids.add(id);

                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return ids;
    }

    public List<String> getUnValidatedClientBaseEntityIds(int limit) {
        List<String> ids = new ArrayList<>();

        final String validateFilter = " where "
                + client_column.syncStatus + " = ? "
                + " AND ( " + client_column.validationStatus + " is NULL or "
                + client_column.validationStatus + " != ? ) ";

        String query = "select "
                + client_column.baseEntityId
                + " from "
                + Table.client.name()
                + validateFilter
                + ORDER_BY
                + client_column.updatedAt
                + " asc limit "
                + limit;

        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery(query, new String[]{BaseRepository.TYPE_Synced, BaseRepository.TYPE_Valid});
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String id = cursor.getString(0);
                    ids.add(id);

                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return ids;
    }

    public void markAllAsUnSynced() {

        String events = "select "
                + event_column.baseEntityId
                + ","
                + event_column.syncStatus
                + " from "
                + Table.event.name();
        String clients = "select "
                + client_column.baseEntityId
                + ","
                + client_column.syncStatus
                + " from "
                + Table.client.name();
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery(clients, null);

            while (cursor.moveToNext()) {
                String beid = (cursor.getString(0));
                if (StringUtils.isBlank(beid)
                        || "{}".equals(beid)) { // Skip blank/empty json string
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put(client_column.baseEntityId.name(), beid);
                values.put(client_column.syncStatus.name(), BaseRepository.TYPE_Unsynced);

                getWritableDatabase().update(Table.client.name(),
                        values,
                        client_column.baseEntityId.name() + " = ?",
                        new String[]{beid});

            }
            cursor.close();
            cursor = getWritableDatabase().rawQuery(events, null);

            while (cursor.moveToNext()) {
                String beid = (cursor.getString(0));
                if (StringUtils.isBlank(beid)
                        || "{}".equals(beid)) { // Skip blank/empty json string
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put(event_column.baseEntityId.name(), beid);
                values.put(event_column.syncStatus.name(), BaseRepository.TYPE_Unsynced);

                getWritableDatabase().update(Table.event.name(),
                        values,
                        event_column.baseEntityId.name() + " = ?",
                        new String[]{beid});

            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

    public JSONObject getClient(SQLiteDatabase db, String baseEntityId) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT json FROM "
                    + Table.client.name()
                    + " WHERE "
                    + client_column.baseEntityId.name()
                    + "= ? ", new String[]{baseEntityId});
            if (cursor.moveToNext()) {
                String jsonEventStr = (cursor.getString(0));
                jsonEventStr = jsonEventStr.replaceAll("'", "");
                JSONObject cl = new JSONObject(jsonEventStr);

                return cl;
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public List<JSONObject> getEventsByBaseEntityId(String baseEntityId) {
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (StringUtils.isBlank(baseEntityId)) {
            return list;
        }

        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT json FROM "
                    + Table.event.name()
                    + " WHERE "
                    + event_column.baseEntityId.name()
                    + "= ? ", new String[]{baseEntityId});
            while (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                JSONObject ev = new JSONObject(jsonEventStr);

                if (ev.has(event_column.baseEntityId.name())) {
                    JSONObject cl = getClient(getWritableDatabase(), baseEntityId);
                    ev.put("client", cl);
                }
                list.add(ev);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public JSONObject getEventsByEventId(String eventId) {
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (StringUtils.isBlank(eventId)) {
            return null;
        }

        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT json FROM "
                    + Table.event.name()
                    + " WHERE "
                    + event_column.eventId.name()
                    + "= ? ", new String[]{eventId});
            if (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                return new JSONObject(jsonEventStr);

            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public JSONObject getEventsByFormSubmissionId(String formSubmissionId) {
        List<JSONObject> list = new ArrayList<JSONObject>();
        if (StringUtils.isBlank(formSubmissionId)) {
            return null;
        }

        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT json FROM "
                    + Table.event.name()
                    + " WHERE "
                    + event_column.formSubmissionId.name()
                    + "= ? ", new String[]{formSubmissionId});
            if (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                return new JSONObject(jsonEventStr);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public JSONObject getClientByBaseEntityId(String baseEntityId) {
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT "
                    + client_column.json
                    + " FROM "
                    + Table.client.name()
                    + " WHERE "
                    + client_column.baseEntityId.name()
                    + " = ? ", new String[]{baseEntityId});
            if (cursor.moveToNext()) {
                String jsonString = cursor.getString(0);
                jsonString = jsonString.replaceAll("'", "");
                return new JSONObject(jsonString);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public Client fetchClientByBaseEntityId(String baseEntityId) {
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT "
                    + client_column.json
                    + " FROM "
                    + Table.client.name()
                    + " WHERE "
                    + client_column.baseEntityId.name()
                    + " = ? ", new String[]{baseEntityId});
            if (cursor.moveToNext()) {
                String jsonString = cursor.getString(0);
                jsonString = jsonString.replaceAll("'", "");
                return convert(jsonString, Client.class);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public JSONObject getUnSyncedClientByBaseEntityId(String baseEntityId) {
        Cursor cursor = null;
        try {
            cursor = getWritableDatabase().rawQuery("SELECT "
                    + client_column.json
                    + " FROM "
                    + Table.client.name()
                    + " WHERE "
                    + client_column.syncStatus.name()
                    + " = ? and "
                    + client_column.baseEntityId.name()
                    + " = ? ", new String[]{BaseRepository.TYPE_Unsynced, baseEntityId});
            if (cursor.moveToNext()) {
                String json = cursor.getString(0);
                json = json.replaceAll("'", "");
                return new JSONObject(json);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public JSONObject getEventsByBaseEntityIdAndEventType(String baseEntityId, String eventType) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery("SELECT json FROM "
                    + Table.event.name()
                    + " WHERE "
                    + event_column.baseEntityId.name()
                    + "= ? AND " + event_column.eventType.name() + "= ? ", new String[]{baseEntityId, eventType});
            if (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");

                return new JSONObject(jsonEventStr);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }


    public List<EventClient> getEventsByBaseEntityIdsAndSyncStatus(String syncStatus, List<String> baseEntityIds) {
        List<EventClient> list = new ArrayList<>();
        if (Utils.isEmptyCollection(baseEntityIds))
            return list;
        Cursor cursor = null;
        try {
            int len = baseEntityIds.size();
            String query = String.format("SELECT json FROM "
                            + Table.event.name()
                            + " WHERE " + event_column.baseEntityId.name() + " IN (%s) "
                            + " AND " + event_column.syncStatus.name() + "= ? "
                            + " ORDER BY " + event_column.serverVersion.name(),
                    TextUtils.join(",", Collections.nCopies(len, "?")));
            String[] params = baseEntityIds.toArray(new String[len + 1]);
            params[len] = syncStatus;
            cursor = getReadableDatabase().rawQuery(query, params);

            while (cursor.moveToNext()) {
                String jsonEventStr = cursor.getString(0);

                jsonEventStr = jsonEventStr.replaceAll("'", "");
                Event event = convert(jsonEventStr, Event.class);
                list.add(new EventClient(event));
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }


    public void addorUpdateClient(String baseEntityId, JSONObject jsonObject) {
        try {
            ContentValues values = new ContentValues();
            values.put(client_column.json.name(), jsonObject.toString());
            values.put(client_column.updatedAt.name(), dateFormat.format(new Date()));
            values.put(client_column.syncStatus.name(), BaseRepository.TYPE_Unsynced);
            values.put(client_column.baseEntityId.name(), baseEntityId);
            populateAdditionalColumns(values, client_column.values(), jsonObject);
            if (checkIfExists(Table.client, baseEntityId)) {
                getWritableDatabase().update(Table.client.name(),
                        values,
                        client_column.baseEntityId.name() + " = ?",
                        new String[]{baseEntityId});
            } else {
                getWritableDatabase().insert(Table.client.name(), null, values);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }

    public void addEvent(String baseEntityId, JSONObject jsonObject) {
        try {
            final String EVENT_TYPE = "eventType";
            ContentValues values = new ContentValues();
            values.put(event_column.json.name(), jsonObject.toString());
            values.put(event_column.eventType.name(),
                    jsonObject.has(EVENT_TYPE) ? jsonObject.getString(EVENT_TYPE) : "");
            values.put(event_column.updatedAt.name(), dateFormat.format(new Date()));
            values.put(event_column.baseEntityId.name(), baseEntityId);
            values.put(event_column.syncStatus.name(), BaseRepository.TYPE_Unsynced);
            if (jsonObject.has(EVENT_ID))
                values.put(event_column.eventId.name(), jsonObject.getString(EVENT_ID));
            populateAdditionalColumns(values, event_column.values(), jsonObject);
            //update existing event if eventid present
            if (jsonObject.has(event_column.formSubmissionId.name())
                    && jsonObject.getString(event_column.formSubmissionId.name()) != null) {
                //sanity check
                if (checkIfExistsByFormSubmissionId(Table.event,
                        jsonObject.getString(event_column
                                .formSubmissionId
                                .name()))) {
                    getWritableDatabase().update(Table.event.name(),
                            values,
                            event_column.formSubmissionId.name() + "=?",
                            new String[]{jsonObject.getString(
                                    event_column.formSubmissionId.name())});
                } else {
                    //that odd case
                    values.put(event_column.formSubmissionId.name(),
                            jsonObject.getString(event_column.formSubmissionId.name()));

                    getWritableDatabase().insert(Table.event.name(), null, values);

                }
            } else {
// a case here would be if an event comes from openmrs
                getWritableDatabase().insert(Table.event.name(), null, values);
            }

        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }


    public void markEventAsSynced(String formSubmissionId) {
        try {

            ContentValues values = new ContentValues();
            values.put(event_column.formSubmissionId.name(), formSubmissionId);
            values.put(event_column.syncStatus.name(), BaseRepository.TYPE_Synced);

            getWritableDatabase().update(Table.event.name(),
                    values,
                    event_column.formSubmissionId.name() + " = ?",
                    new String[]{formSubmissionId});

        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }

    public void markClientAsSynced(String baseEntityId) {
        try {

            ContentValues values = new ContentValues();
            values.put(client_column.baseEntityId.name(), baseEntityId);
            values.put(client_column.syncStatus.name(), BaseRepository.TYPE_Synced);

            getWritableDatabase().update(Table.client.name(),
                    values,
                    client_column.baseEntityId.name() + " = ?",
                    new String[]{baseEntityId});

        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }

    public void markEventValidationStatus(String formSubmissionId, boolean valid) {
        try {
            ContentValues values = new ContentValues();
            values.put(event_column.formSubmissionId.name(), formSubmissionId);
            values.put(event_column.validationStatus.name(), valid ? TYPE_Valid : TYPE_InValid);
            if (!valid) {
                values.put(event_column.syncStatus.name(), TYPE_Unsynced);
            }

            getWritableDatabase().update(Table.event.name(),
                    values,
                    event_column.formSubmissionId.name() + " = ?",
                    new String[]{formSubmissionId});

        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }


    public void markClientValidationStatus(String baseEntityId, boolean valid) {
        try {
            ContentValues values = new ContentValues();
            values.put(client_column.baseEntityId.name(), baseEntityId);
            values.put(client_column.validationStatus.name(), valid ? TYPE_Valid : TYPE_InValid);
            if (!valid) {
                values.put(client_column.syncStatus.name(), TYPE_Unsynced);
            }

            getWritableDatabase().update(Table.client.name(),
                    values,
                    client_column.baseEntityId.name() + " = ?",
                    new String[]{baseEntityId});

        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }

    public void markEventAsTaskUnprocessed(String formSubmissionId) {
        try {
            ContentValues values = new ContentValues();
            values.put(client_column.syncStatus.name(), TYPE_Task_Unprocessed);
            getWritableDatabase().update(Table.event.name(),
                    values,
                    event_column.formSubmissionId.name() + " = ?",
                    new String[]{formSubmissionId});
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void markEventsAsSynced(Map<String, Object> syncedEvents) {
        try {
            List<JSONObject> clients =
                    syncedEvents.containsKey("clients") ? (List<JSONObject>) syncedEvents.get(
                            "clients") : null;
            List<JSONObject> events =
                    syncedEvents.containsKey("events") ? (List<JSONObject>) syncedEvents.get(
                            "events") : null;

            if (clients != null && !clients.isEmpty()) {
                for (JSONObject client : clients) {
                    String baseEntityId = client.getString(client_column.baseEntityId.name());
                    markClientAsSynced(baseEntityId);
                }
            }
            if (events != null && !events.isEmpty()) {
                for (JSONObject event : events) {
                    String formSubmissionId = event.getString(event_column.formSubmissionId.name());
                    markEventAsSynced(formSubmissionId);
                }
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }

    }

    public static String getCreateTableColumn(Column col) {
        ColumnAttribute c = col.column();
        return "`" + col.name() + "` " + getSqliteType(c.type()) + (c.pk() ? " PRIMARY KEY " : "");
    }

    public static String removeEndingComma(String str) {
        if (str.trim().endsWith(",")) {
            return str.substring(0, str.lastIndexOf(","));
        }
        return str;
    }

    public static void createTable(SQLiteDatabase db, BaseTable table, Column[] columns) {
        try {
            String cl = "";
            for (Column cc : columns) {
                cl += getCreateTableColumn(cc) + ",";
            }
            cl = removeEndingComma(cl);
            String create_tb = "CREATE TABLE " + table.name() + " ( " + cl + " )";

            db.execSQL(create_tb);

            createIndex(db, table, columns);
        } catch (Exception e) {
            Log.e(EventClientRepository.class.getName(), "Exception", e);
        }
    }

    public static void createIndex(SQLiteDatabase db, BaseTable table, Column[] columns) {
        try {
            for (Column cc : columns) {
                if (cc.column().index()) {
                    String create_id = "CREATE INDEX IF NOT EXISTS "
                            + table.name() + "_" + cc.name()
                            + "_index ON "
                            + table.name()
                            + " ("
                            + cc.name()
                            + "); ";
                    db.execSQL(create_id);
                }
            }
        } catch (Exception e) {
            Log.e(EventClientRepository.class.getName(), "Exception", e);
        }
    }

    public static void dropIndexes(SQLiteDatabase db, BaseTable table) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type = ?"
                    + " AND sql is not null AND tbl_name = ?", new String[]{"index", table.name()});
            while (cursor.moveToNext()) {
                db.execSQL("DROP INDEX " + cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(EventClientRepository.class.getName(), "SQLException", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    // Definitions
    public enum Table implements BaseTable {
        client(client_column.values()),
        event(event_column.values());
        private Column[] columns;

        public Column[] columns() {
            return columns;
        }

        Table(Column[] columns) {
            this.columns = columns;
        }
    }

    public enum client_column implements Column {

        baseEntityId(ColumnAttribute.Type.text, true, true),
        syncStatus(ColumnAttribute.Type.text, false, true),
        validationStatus(ColumnAttribute.Type.text, false, true),
        json(ColumnAttribute.Type.text, false, false),
        updatedAt(ColumnAttribute.Type.date, false, true);

        client_column(ColumnAttribute.Type type, boolean pk, boolean index) {
            this.column = new ColumnAttribute(type, pk, index);
        }

        private ColumnAttribute column;

        public ColumnAttribute column() {
            return column;
        }
    }

    public enum event_column implements Column {
        dateCreated(ColumnAttribute.Type.date, false, true),
        dateEdited(ColumnAttribute.Type.date, false, false),

        eventId(ColumnAttribute.Type.text, true, true),
        baseEntityId(ColumnAttribute.Type.text, false, true),
        syncStatus(ColumnAttribute.Type.text, false, true),
        validationStatus(ColumnAttribute.Type.text, false, true),
        json(ColumnAttribute.Type.text, false, false),
        eventDate(ColumnAttribute.Type.date, false, true),
        eventType(ColumnAttribute.Type.text, false, true),
        formSubmissionId(ColumnAttribute.Type.text, false, true),
        updatedAt(ColumnAttribute.Type.date, false, true),
        serverVersion(ColumnAttribute.Type.longnum, false, true);

        event_column(ColumnAttribute.Type type, boolean pk, boolean index) {
            this.column = new ColumnAttribute(type, pk, index);
        }

        private ColumnAttribute column;

        public ColumnAttribute column() {
            return column;
        }
    }

    public static String getSqliteType(ColumnAttribute.Type type) {
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.text.name())) {
            return "varchar";
        }
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.bool.name())) {
            return "boolean";
        }
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.date.name())) {
            return "datetime";
        }
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.list.name())) {
            return "varchar";
        }
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.map.name())) {
            return "varchar";
        }
        if (type.name().equalsIgnoreCase(ColumnAttribute.Type.longnum.name())) {
            return "integer";
        }
        return null;
    }

    public boolean deleteClient(String baseEntityId) {
        try {
            int rowsAffected = getWritableDatabase().delete(Table.client.name(),
                    client_column.baseEntityId.name()
                            + " = ?",
                    new String[]{baseEntityId});
            if (rowsAffected > 0) {
                return true;
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
        return false;
    }

    public boolean deleteEventsByBaseEntityId(String baseEntityId, String eventType) {

        try {
            int rowsAffected = getWritableDatabase().delete(Table.event.name(),
                    event_column.baseEntityId.name()
                            + " = ? AND "
                            + event_column.eventType.name()
                            + " != ?",
                    new String[]{baseEntityId, eventType});
            if (rowsAffected > 0) {
                return true;
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception", e);
        }
        return false;
    }

    static class QueryWrapper {
        public String sqlQuery;
        public Map<String, Integer> columnOrder;
    }
}

package com.activeandroid;

/*
 * Copyright (C) 2010 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Field;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.os.Parcelable;

import com.activeandroid.annotation.Column;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;

@SuppressWarnings("unchecked")
public abstract class Model implements Parcelable {
	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS
	//////////////////////////////////////////////////////////////////////////////////////

	@Column(name = "Id")
	private Long mId = null;

	private TableInfo mTableInfo;

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	public final Long getId() {
		return mId;
	}

	public final void delete() {
		onDelete();
		Model.delete(getClass(), getId());
		Cache.removeEntity(this);
	}

	public final void save() {
		onSave();

		final SQLiteDatabase db = Cache.openDatabase();
		final ContentValues values = new ContentValues();

		for (Field field : getTableInfo().getFields()) {
			final String fieldName = getTableInfo().getColumnName(field);
			Class<?> fieldType = field.getType();

			field.setAccessible(true);

			try {
				Object value = field.get(this);

				if (value != null) {
					final TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
					if (typeSerializer != null) {
						// serialize data
						value = typeSerializer.serialize(value);
						// set new object type
						if (value != null) {
							fieldType = value.getClass();
							// check that the serializer returned what it promised
							if (!fieldType.equals(typeSerializer.getSerializedType())) {
								Log.w(String.format("TypeSerializer returned wrong type: expected a %s but got a %s",
										typeSerializer.getSerializedType(), fieldType));
							}
						}
					}
				}

				// TODO: Find a smarter way to do this? This if block is necessary because we
				// can't know the type until runtime.
				if (value == null) {
					values.putNull(fieldName);
				}
				else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) {
					values.put(fieldName, (Byte) value);
				}
				else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
					values.put(fieldName, (Short) value);
				}
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					values.put(fieldName, (Integer) value);
				}
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					values.put(fieldName, (Long) value);
				}
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					values.put(fieldName, (Float) value);
				}
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					values.put(fieldName, (Double) value);
				}
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					values.put(fieldName, (Boolean) value);
				}
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					values.put(fieldName, value.toString());
				}
				else if (fieldType.equals(String.class)) {
					values.put(fieldName, value.toString());
				}
				else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
					values.put(fieldName, (byte[]) value);
				}
				else if (ReflectionUtils.isModel(fieldType)) {
					values.put(fieldName, ((Model) value).getId());
				}
				else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)){
					values.put(fieldName, ((Enum<?>) value).name());
				}
			}
			catch (IllegalArgumentException e) {
				Log.e(e.getClass().getName(), e);
			}
			catch (IllegalAccessException e) {
				Log.e(e.getClass().getName(), e);
			}
		}

		if (mId == null) {
			mId = db.insert(getTableInfo().getTableName(), null, values);
		}
		else {
			db.update(getTableInfo().getTableName(), values, "Id=" + mId, null);
		}

		Cache.addEntity(this);
		mTableInfo.notifyChanged();
	}

	// Convenience methods

	public static void delete(Class<? extends Model> type, long id) {
		new Delete().from(type).where("Id=?", id).execute();
		Cache.getTableInfo(type).notifyChanged();
	}

	public static <T extends Model> T load(Class<? extends Model> type, long id) {
		return new Select().from(type).where("Id=?", id).executeSingle();
	}

	public static <T extends Model> List<T> all(Class<? extends Model> type) {
		return new Select().from(type).execute();
	}

	public static void registerDataSetObserver(Class<? extends Model> type, DataSetObserver observer) {
		Cache.getTableInfo(type).registerObserver(observer);
	}

	public static void unregisterDataSetObserver(Class<? extends Model> type, DataSetObserver observer) {
		Cache.getTableInfo(type).unregisterObserver(observer);
	}

	// Model population

	public final void loadFromCursor(Class<? extends Model> type, Cursor cursor) {
		for (Field field : getTableInfo().getFields()) {
			final String fieldName = getTableInfo().getColumnName(field);
			Class<?> fieldType = field.getType();
			final int columnIndex = cursor.getColumnIndex(fieldName);

			if (columnIndex < 0) {
				continue;
			}

			field.setAccessible(true);

			try {
				boolean columnIsNull = cursor.isNull(columnIndex);
				TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
				Object value = null;

				if (typeSerializer != null) {
				  fieldType = typeSerializer.getSerializedType();
				}

				// TODO: Find a smarter way to do this? This if block is necessary because we
				// can't know the type until runtime.
				if (columnIsNull) {
					field = null;
				}
				else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					value = cursor.getLong(columnIndex);
				}
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					value = cursor.getFloat(columnIndex);
				}
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					value = cursor.getDouble(columnIndex);
				}
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					value = cursor.getInt(columnIndex) != 0;
				}
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					value = cursor.getString(columnIndex).charAt(0);
				}
				else if (fieldType.equals(String.class)) {
					value = cursor.getString(columnIndex);
				}
				else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
					value = cursor.getBlob(columnIndex);
				}
				else if (ReflectionUtils.isModel(fieldType)) {
					final long entityId = cursor.getLong(columnIndex);
					final Class<? extends Model> entityType = (Class<? extends Model>) fieldType;

					Model entity = Cache.getEntity(entityType, entityId);
					if (entity == null) {
						entity = new Select().from(entityType).where("Id=?", entityId).executeSingle();
					}

					value = entity;
				}
				else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)){
					@SuppressWarnings("rawtypes")
					final Class<? extends Enum> enumType =  (Class<? extends Enum>) fieldType;
					value=Enum.valueOf(enumType, cursor.getString(columnIndex));
				}

				// Use a deserializer if one is available
				if (typeSerializer != null && !columnIsNull) {
					value = typeSerializer.deserialize(value);
				}

				// Set the field value
				if (value != null) {
					field.set(this, value);
				}

				Cache.addEntity(this);
			}
			catch (IllegalArgumentException e) {
				Log.e(e.getMessage());
			}
			catch (IllegalAccessException e) {
				Log.e(e.getMessage());
			}
			catch (SecurityException e) {
				Log.e(e.getMessage());
			}
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PROTECTED METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	protected final <E extends Model> List<E> getMany(Class<? extends Model> type, String foreignKey) {
		return new Select().from(type).where(Cache.getTableName(type) + "." + foreignKey + "=?", getId()).execute();
	}

	/**
	 * Called before {@link save} does any work.
	 */
	protected void onSave(){
	}

	/**
	 * Called before {@link delete} does any work.
	 */
	protected void onDelete(){
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// OVERRIDEN METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	@Override
	public boolean equals(Object obj) {
		final Model other = (Model) obj;

		return this.mId != null && (this.getTableInfo().getTableName().equals(other.getTableInfo().getTableName()))
				&& (this.mId.equals(other.mId));
	}

	private TableInfo getTableInfo() {
		if (mTableInfo == null) {
			mTableInfo = Cache.getTableInfo(getClass());
		}
		return mTableInfo;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	// PARCELABLE
	//////////////////////////////////////////////////////////////////////////////////////

	public int describeContents() {
		return 0;
	}
	
	 public void writeToParcel(Parcel out, int flags) {
         out.writeLong(getId());
         out.writeSerializable(getClass());
     }

     public static final Parcelable.Creator<Model> CREATOR
             = new Parcelable.Creator<Model>() {
         public Model createFromParcel(Parcel in) {
        	 long id = in.readLong();
        	 Class<Model> modelClass = (Class<Model>) in.readSerializable();
             return Model.load(modelClass, id);
         }

         public Model[] newArray(int size) {
             return new Model[size];
         }
     };
}

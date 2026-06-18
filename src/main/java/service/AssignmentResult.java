package service;

import model.Courier;
import model.Parcel;

public record AssignmentResult(boolean assigned, String message, Parcel parcel, Courier courier) {
}

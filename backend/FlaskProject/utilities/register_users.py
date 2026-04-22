"""
Utility script to register users (Manager / Nurse) in Firebase Auth + Firestore.

Usage:
    python register_users.py

Edit the USERS list below to add/change users before running.
"""

import sys
import os

# Add parent directory to path so we can reuse the same firebase_service_account.json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import firebase_admin
from firebase_admin import credentials, firestore, auth

# Initialize Firebase Admin SDK
cred = credentials.Certificate(
    os.path.join(os.path.dirname(__file__), "..", "firebase_service_account.json")
)
firebase_admin.initialize_app(cred)

db = firestore.client()

# ── Define users to register ──────────────────────────────────────────
# Each user needs: name, email, password, role ("manager" or "nurse")
# For nurses, also provide: id_number, phone

USERS = [
    # ── Manager ──
    {"name": "Nadav Shalev", "email": "nadav@nureform.com", "password": "Manager123!", "role": "manager"},
    # ── Nurses ──
    {"name": "Dana Cohen", "email": "dana@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000001", "phone": "050-1111111"},
    {"name": "Tamar Levi", "email": "tamar@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000002", "phone": "050-2222222"},
    {"name": "Lior Ben David", "email": "lior@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000003", "phone": "050-3333333"},
    {"name": "Shir Avraham", "email": "shir@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000004", "phone": "050-4444444"},
    {"name": "Ron Mizrahi", "email": "ron@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000005", "phone": "050-5555555"},
    {"name": "Eden Katz", "email": "eden@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000006", "phone": "050-6666666"},
    {"name": "Mor Peretz", "email": "mor@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000007", "phone": "050-7777777"},
    {"name": "Ayala Friedman", "email": "ayala@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000008", "phone": "050-8888888"},
    {"name": "Or Shapira", "email": "or@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000009", "phone": "050-9999999"},
    {"name": "Iris Goldstein", "email": "iris@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000010", "phone": "052-1111111"},
    {"name": "Leah Rosenberg", "email": "leah@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000011", "phone": "052-2222222"},
    {"name": "Noa Tal", "email": "noa@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000012", "phone": "052-3333333"},
    {"name": "Yael Alon", "email": "yael@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000013", "phone": "052-4444444"},
    {"name": "Rotem Hadar", "email": "rotem@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000014", "phone": "052-5555555"},
    {"name": "Ofir Dahan", "email": "ofir@nureform.com", "password": "Nurse123!", "role": "nurse", "id_number": "200000015", "phone": "052-6666666"},
]


def register_user(user: dict):
    """Create a Firebase Auth user and corresponding Firestore documents."""
    name = user["name"]
    email = user["email"]
    password = user["password"]
    role = user["role"]

    # 1. Create Firebase Auth account
    try:
        auth_user = auth.create_user(
            email=email,
            password=password,
            display_name=name,
        )
        uid = auth_user.uid
        print(f"  [Auth] Created user: {email} (uid: {uid})")
    except auth.EmailAlreadyExistsError:
        # User already exists, fetch their uid
        auth_user = auth.get_user_by_email(email)
        uid = auth_user.uid
        print(f"  [Auth] User already exists: {email} (uid: {uid})")

    # 2. Create Firestore document in 'users' collection
    db.collection("users").document(uid).set({
        "uid": uid,
        "name": name,
        "role": role,
    })
    print(f"  [Firestore] users/{uid} -> role: {role}")

    # 3. If nurse, also create document in 'nurses' collection
    if role == "nurse":
        import time
        db.collection("nurses").document(uid).set({
            "idNumber": user.get("id_number", ""),
            "name": name,
            "phone": user.get("phone", ""),
            "email": email,
            "userId": uid,
            "createdAt": int(time.time() * 1000),
        })
        print(f"  [Firestore] nurses/{uid} -> {name}")

    return uid


def main():
    print(f"\nRegistering {len(USERS)} user(s)...\n")

    for i, user in enumerate(USERS, 1):
        print(f"[{i}/{len(USERS)}] {user['name']} ({user['role']})")
        register_user(user)
        print()

    print("Done!")


if __name__ == "__main__":
    main()

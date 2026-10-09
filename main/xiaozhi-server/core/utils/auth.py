import jwt
import time
import json
import os
from datetime import datetime, timedelta, timezone
from typing import Tuple, Optional
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.backends import default_backend
import base64


class AuthToken:
    def __init__(self, secret_key: str):
        self.secret_key = secret_key.encode()  # convert to bytes
        # Derive a fixed-length encryption key from the secret (32 bytes for AES-256)
        self.encryption_key = self._derive_key(32)

    def _derive_key(self, length: int) -> bytes:
        """Derive a fixed-length key"""
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

        # Use a fixed salt (production should use a random salt)
        salt = b"fixed_salt_placeholder"  # should be randomly generated in production
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=length,
            salt=salt,
            iterations=100000,
            backend=default_backend(),
        )
        return kdf.derive(self.secret_key)

    def _encrypt_payload(self, payload: dict) -> str:
        """Encrypt the whole payload with AES-GCM"""
        # Convert the payload to a JSON string
        payload_json = json.dumps(payload)

        # Generate a random IV
        iv = os.urandom(12)
        # Create the encryptor
        cipher = Cipher(
            algorithms.AES(self.encryption_key),
            modes.GCM(iv),
            backend=default_backend(),
        )
        encryptor = cipher.encryptor()

        # Encrypt and generate the tag
        ciphertext = encryptor.update(payload_json.encode()) + encryptor.finalize()
        tag = encryptor.tag

        # Combine IV + ciphertext + tag
        encrypted_data = iv + ciphertext + tag
        return base64.urlsafe_b64encode(encrypted_data).decode()

    def _decrypt_payload(self, encrypted_data: str) -> dict:
        """Decrypt an AES-GCM encrypted payload"""
        # Decode Base64
        data = base64.urlsafe_b64decode(encrypted_data.encode())
        # Split components
        iv = data[:12]
        tag = data[-16:]
        ciphertext = data[12:-16]

        # Create the decryptor
        cipher = Cipher(
            algorithms.AES(self.encryption_key),
            modes.GCM(iv, tag),
            backend=default_backend(),
        )
        decryptor = cipher.decryptor()

        # Decrypt
        plaintext = decryptor.update(ciphertext) + decryptor.finalize()
        return json.loads(plaintext.decode())

    def generate_token(self, device_id: str) -> str:
        """
        Generate a JWT token
        :param device_id: device ID
        :return: JWT token string
        """
        # Set expiration to 1 hour from now
        expire_time = datetime.now(timezone.utc) + timedelta(hours=1)

        # Create the raw payload
        payload = {"device_id": device_id, "exp": expire_time.timestamp()}

        # Encrypt the whole payload
        encrypted_payload = self._encrypt_payload(payload)

        # Create the outer payload containing the encrypted data
        outer_payload = {"data": encrypted_payload}

        # Encode with JWT
        token = jwt.encode(outer_payload, self.secret_key, algorithm="HS256")
        return token

    def verify_token(self, token: str) -> Tuple[bool, Optional[str]]:
        """
        Verify a token
        :param token: JWT token string
        :return: (is_valid, device ID)
        """
        try:
            # First verify the outer JWT (signature and expiration)
            outer_payload = jwt.decode(token, self.secret_key, algorithms=["HS256"])

            # Decrypt the inner payload
            inner_payload = self._decrypt_payload(outer_payload["data"])

            # Check expiration again (double check)
            if inner_payload["exp"] < time.time():
                return False, None

            return True, inner_payload["device_id"]

        except jwt.InvalidTokenError:
            return False, None
        except json.JSONDecodeError:
            return False, None
        except Exception as e:  # catch any other errors
            print(f"Token verification failed: {str(e)}")
            return False, None

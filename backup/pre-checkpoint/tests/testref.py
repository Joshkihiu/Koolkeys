import secrets
import string

def generate_password():
    characters = string.ascii_letters + string.digits
    return ''.join(secrets.choice(characters) for _ in range(12))

print(generate_password())
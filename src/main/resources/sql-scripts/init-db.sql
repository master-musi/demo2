-- Create the users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                              );

-- Optional seed data
INSERT INTO users (name, email) VALUES
                                    ('Alice Johnson', 'alice@example.com'),
                                    ('Bob Smith',     'bob@example.com')
    ON CONFLICT (email) DO NOTHING;
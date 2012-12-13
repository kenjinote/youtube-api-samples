#!/usr/bin/ruby

require 'rubygems'
require 'google/api_client'
require 'logger'
require 'oauth/oauth_util'
require 'sqlite3'
require 'thread'

YOUTUBE_API_READONLY_SCOPE = 'https://www.googleapis.com/auth/youtube.readonly'
YOUTUBE_ANALYTICS_API_SCOPE = 'https://www.googleapis.com/auth/yt-analytics.readonly'
SQL_LITE_DB_FILE = 'video_analytics.db'
CREATE_TABLE_SQL = 'CREATE TABLE IF NOT EXISTS video_analytics (date date, video text, %s PRIMARY KEY(date, video))'
CREATE_INDEX_SQL = 'CREATE INDEX IF NOT EXISTS video_date_index ON video_analytics (video, date ASC)'
INSERT_SQL = 'INSERT INTO video_analytics VALUES (%s)'
METRICS = %w'views comments favoritesAdded favoritesRemoved likes dislikes shares'
SECONDS_IN_DAY = 60 * 60 * 24
YOUTUBE_ANALYTICS_API_THREADS = 5

Log = Logger.new(STDOUT)

def initialize_log
  Log.formatter = proc do |severity, time, progname, msg|
    timestamp = time.strftime('%Y-%m-%d %H:%M:%S')
    thread_name = Thread.current[:name] || 'Main'
    "#{timestamp} [#{thread_name}] #{severity}: #{msg}\n"
  end
end

def initialize_api_clients
  client = Google::APIClient.new
  youtube = client.discovered_api('youtube', 'v3')
  youtube_analytics = client.discovered_api('youtubeAnalytics', 'v1')

  auth_util = CommandLineOAuthHelper.new([YOUTUBE_API_READONLY_SCOPE, YOUTUBE_ANALYTICS_API_SCOPE])
  client.authorization = auth_util.authorize()

  return client, youtube, youtube_analytics
end

def get_channels(client, youtube)
  begin
    channels_list_response = client.execute!(
      :api_method => youtube.channels.list,
      :parameters => {
        :mine => true,
        :part => 'contentDetails'
      }
    )

    return channels_list_response.data.items
  rescue Google::APIClient::ClientError => client_error
    Log.error(client_error)
  end
end

def get_ids(client, youtube, channels)
  ids = Queue.new

  channels.each do |channel|
    channel_id = channel.id
    Log.info("Getting videos in channel id #{channel_id}...")
    uploads_list_id = channel.contentDetails.relatedPlaylists.uploads
    next_page_token = ''

    until next_page_token.nil?
      Log.debug("Fetching #{uploads_list_id} with page token #{next_page_token}...")
      playlistitems_list_response = client.execute!(
        :api_method => youtube.playlist_items.list,
        :parameters => {
          :playlistId => uploads_list_id,
          :part => 'snippet',
          :maxResults => 50,
          :pageToken => next_page_token
        }
      )

      playlistitems_list_response.data.items.each do |playlist_item|
        video_id = playlist_item.snippet.resourceId.videoId
        Log.debug("Found #{video_id} in channel #{channel_id}")
        ids << {
          :channel_id => channel_id,
          :video_id => video_id
        }
      end

      next_page_token = playlistitems_list_response.data.next_page_token
    end
  end

  return ids
end

def run_reports(client, youtube_analytics, ids, date)
  report_rows = []
  threads = []

  YOUTUBE_ANALYTICS_API_THREADS.times do |count|
    threads << Thread.new do
      Thread.current[:name] = "#{__method__}-#{count}"
      until ids.empty?
        id = ids.pop(true) rescue nil
        if id
          video_id = id[:video_id]
          channel_id = id[:channel_id]
          Log.info("Running report for video id #{video_id} in channel #{channel_id}...")

          begin
            reports_query_response = client.execute!(
              :api_method => youtube_analytics.reports.query,
              :parameters => {
                :metrics => METRICS.join(','),
                'start-date' => date,
                'end-date' => date,
                :ids => "channel==#{channel_id}",
                :filters => "video==#{video_id}"
              }
            )
          rescue Google::APIClient::ClientError => client_error
            import 'pp'
            Log.error(client_error.pretty_print_inspect)
          end

          report_rows << {
            :video_id => video_id,
            :row => reports_query_response.data.rows[0]
          }
        end
      end
    end
  end

  threads.each do |thread|
    thread.join
  end

  return report_rows
end

def create_table_if_needed(db)
  columns = METRICS.join(' real, ') + ' real,'
  create_table_sql = CREATE_TABLE_SQL % columns
  Log.debug("Executing: #{create_table_sql}")
  db.execute(create_table_sql)
  db.execute(CREATE_INDEX_SQL)
end

def insert_into_db(db, report_rows, date)
  question_marks = '?,' * (2 + METRICS.length)
  question_marks = question_marks[0..-2]
  insert_sql = INSERT_SQL % question_marks

  report_rows.each do |report_row|
    begin
      Log.debug("Executing: #{insert_sql} with values (#{report_row[:video_id]}, #{date}, #{report_row[:row].join(', ')})")
      db.execute(insert_sql, report_row[:video_id], date, report_row[:row])
    rescue SQLite3::ConstraintException => constraint_exception
      Log.error("Inserting #{report_row[:video_id]} failed: #{constraint_exception}")
    end
  end
end

if __FILE__ == $PROGRAM_NAME
  initialize_log()
  Log.info('Starting up...')

  client, youtube, youtube_analytics = initialize_api_clients()
  channels = get_channels(client, youtube)
  ids = get_ids(client, youtube, channels)

  yesterday = Time.at(Time.new.to_i - SECONDS_IN_DAY).strftime('%Y-%m-%d')
  report_rows = run_reports(client, youtube_analytics, ids, yesterday)

  db = SQLite3::Database.new(SQL_LITE_DB_FILE)
  create_table_if_needed(db)
  insert_into_db(db, report_rows, yesterday)

  Log.info('All done!')
end
package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path"
	"runtime"

	"code.google.com/p/goauth2/oauth"
	"code.google.com/p/google-api-go-client/youtube/v3"
)

var (
	clientSecretsFile = flag.String("secrets", "client_secrets.json", "Client Secrets configuration")
	cachefile         = flag.String("cache", "request.token", "Token cache file")
)

const missingClientSecretsMessage = `
WARNING: Please configure OAuth 2.0

To make this sample run you will need to populate the client_secrets.json file
found at:

   %v

with information from the APIs Console
https://code.google.com/apis/console#access

For more information about the client_secrets.json file format, please visit:
https://developers.google.com/api-client-library/python/guide/aaa_client_secrets
`

// Data structure definition for client_secrets.json. This is what we'll unmarshal
// the JSON configuration file into
type ClientConfig struct {
	ClientId     string   `json:"client_id"`
	ClientSecret string   `json:"client_secret"`
	RedirectURIs []string `json:"redirect_uris"`
	AuthURI      string   `json:"auth_uri"`
	TokenURI     string   `json:"token_uri"`
}

// Root level configuration object
type Config struct {
	Installed ClientConfig
}

func main() {
	flag.Parse()

	config, err := readConfig()
	if err != nil {
		log.Fatalf("Error reading configuration from %v: %v", *clientSecretsFile, err)
	}

	httpClient, err := buildOAuthHTTPClient(config)
	if err != nil {
		log.Fatalf("Error building OAuth client: %v", err)
	}

	youtube, err := youtube.New(httpClient)
	if err != nil {
		log.Fatalf("Error creating YouTube client: %v", err)
	}

	// Starting making YouTube API calls
	apiCall := youtube.Channels.List("contentDetails").Mine(true)

	response, err := apiCall.Do()
	if err != nil {
		log.Fatalf("Error making API call to list channels: %v", err.Error())
	}

	for _, channel := range response.Items {
		playlistId := channel.ContentDetails.RelatedPlaylists.Uploads
		fmt.Printf("Videos in list %s\r\n", playlistId)

		nextPageToken := ""
		for {
			playlistCall := youtube.PlaylistItems.List("snippet").
				PlaylistId(playlistId).
				MaxResults(50).
				PageToken(nextPageToken)

			playlistResponse, err := playlistCall.Do()

			if err != nil {
				log.Fatalf("Error fetching playlist items: %v", err.Error())
			}

			for _, playlistItem := range playlistResponse.Items {
				title := playlistItem.Snippet.Title
				videoId := playlistItem.Snippet.ResourceId.VideoId
				fmt.Printf("%v, (%v)\r\n", title, videoId)
			}

			nextPageToken = playlistResponse.NextPageToken
			if nextPageToken == "" {
				break
			}
			fmt.Println()
		}
	}

}

// openUrl opens a browser window to that location.
// This code taken from: http://stackoverflow.com/questions/10377243/how-can-i-launch-a-process-that-is-not-a-file-in-go
func openUrl(url string) error {
	var err error
	switch runtime.GOOS {
	case "linux":
		err = exec.Command("xdg-open", url).Start()
	case "windows":
		err = exec.Command("rundll32", url, "http://localhost:4001/").Start()
	case "darwin":
		err = exec.Command("open", url).Start()
	default:
		err = fmt.Errorf("Cannot open URL %s on this platform", url)
	}
	return err

}

// readConfig reads the configuration from clientSecretsFile. Returns an oauth configuration
// object to be used with the Google API client
func readConfig() (*oauth.Config, error) {
	// Let's read the configuration
	configFile := new(Config)
	data, err := ioutil.ReadFile(*clientSecretsFile)
	if err != nil {
		pwd, _ := os.Getwd()
		clientSecretsFileFullPath := path.Join(pwd, *clientSecretsFile)
		return nil, fmt.Errorf(missingClientSecretsMessage, clientSecretsFileFullPath)
	}

	err = json.Unmarshal(data, &configFile)
	if err != nil {
		return nil, err
	}

	if len(configFile.Installed.RedirectURIs) < 1 {
		return nil, errors.New("Configuration file must contain at least one redirect URI")
	}

	return &oauth.Config{
		ClientId:     configFile.Installed.ClientId,
		ClientSecret: configFile.Installed.ClientSecret,
		Scope:        youtube.YoutubeReadonlyScope,
		AuthURL:      configFile.Installed.AuthURI,
		TokenURL:     configFile.Installed.TokenURI,
		RedirectURL:  configFile.Installed.RedirectURIs[0],
		TokenCache:   oauth.CacheFile(*cachefile),
		// This gives us a refresh token so we can use this access token indefinitely
		AccessType: "offline",
		// If we want a refresh token, we must set this to force an approval prompt or this
		// won't work
		ApprovalPrompt: "force",
	}, nil
}

// startWebServer starts a web server that listens on http://localhost:8080. The purpose
// of this webserver is to wait for a oauth code in the three-legged auth flow
func startWebServer(codeChannel chan string) error {
	listener, err := net.Listen("tcp", "localhost:8080")
	if err != nil {
		return err
	}

	go http.Serve(listener, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		code := r.FormValue("code")
		codeChannel <- code // send code to OAuth flow
		listener.Close()
		w.Header().Set("Content-Type", "text/plain")
		fmt.Fprintf(w, "Received code: %v\r\nYou can now safely close this browser window.", code)
	}))

	return nil
}

// buildOAuthHTTPClient takes the user through the three-legged OAuth flow. Opens a browser in the native OS or
// outputs a URL, blocking until the redirect completes to the /oauth2callback URI.
// Returns an instance of an HTTP client that can be passed to the constructor of the YouTube client.
func buildOAuthHTTPClient(config *oauth.Config) (*http.Client, error) {
	transport := &oauth.Transport{Config: config}

	// Try to read the token from the cache file.
	// If there's an error, the token is invalid or doesn't exist, do the 3-legged OAuth flow.
	token, err := config.TokenCache.Token()
	if err != nil {

		// Make a channel for the web server to communicate with us on to tell us we have a code
		codeChannel := make(chan string)

		// Start web server. This is how this program receives the authorization code when the browser redirects.
		err := startWebServer(codeChannel)
		if err != nil {
			return nil, err
		}

		// Open url in browser
		url := config.AuthCodeURL("")
		err = openUrl(url)
		if err != nil {
			fmt.Println("Visit the URL below to get a code. This program will pause until the site is visted.")
		} else {
			fmt.Println("Your browser has been opened to an authorization URL. This program will resume once authorization has been provided.\n")
		}
		fmt.Println(url)

		// Block, wait for the web server to get the code back
		code := <-codeChannel

		// This will take care of caching the code on the local filesystem, if necessary, as long as the TokenCache
		// attribute in the config is set
		token, err = transport.Exchange(code)
		if err != nil {
			return nil, err
		}
	}

	transport.Token = token
	return transport.Client(), nil
}

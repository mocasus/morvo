// Vercel serverless API proxy
// Forwards all requests to Morvo backend (HTTP → HTTPS bridge)

const BACKEND = 'http://43.134.131.85/morvo';

module.exports = async (req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // Parse the target path from the URL
  // /api/proxy/api/v1/health → /api/v1/health
  const url = new URL(req.url, 'http://localhost');
  let targetPath = url.pathname.replace(/^\/api\/proxy/, '') || '/';

  // Append query string if any
  if (url.search) targetPath += url.search;

  const targetUrl = BACKEND + targetPath;

  try {
    const fetchOptions = {
      method: req.method,
      headers: {
        'Content-Type': 'application/json',
      },
    };

    if (req.method === 'POST' && req.body) {
      fetchOptions.body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    }

    const response = await fetch(targetUrl, fetchOptions);
    const data = await response.json();

    res.status(response.status).json(data);
  } catch (error) {
    res.status(502).json({
      error: 'Backend unreachable',
      detail: error.message,
    });
  }
};
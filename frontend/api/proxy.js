// Vercel API proxy — forwards requests to Morvo backend
// This handles CORS and hides the backend URL

const BACKEND = 'http://43.134.131.85/morvo';

export default async function handler(req, res) {
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }
  
  const { path = [] } = req.query;
  const apiPath = '/' + (Array.isArray(path) ? path.join('/') : path);
  const url = BACKEND + apiPath;
  
  try {
    const fetchOpts = {
      method: req.method,
      headers: { 'Content-Type': 'application/json' }
    };
    
    if (req.method === 'POST') {
      fetchOpts.body = JSON.stringify(req.body);
    }
    
    const backendRes = await fetch(url, fetchOpts);
    const data = await backendRes.json();
    
    res.status(backendRes.status).json(data);
  } catch (e) {
    res.status(502).json({ error: 'Backend unreachable', detail: e.message });
  }
}
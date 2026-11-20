// `ng serve` sends /api to the Spring Boot server: port 8080, or API_PORT when that one is taken.
export default {
  '/api': {
    target: `http://localhost:${process.env.API_PORT ?? 8080}`,
    secure: false,
  },
};
